---
layout: post
title: Elasticsearch case study for realtime analytics
date: 2014-05-18
permalink: elasticsearch-case-study
tags: [elasticsearch, case-study, analytics, spray]
---

What we're trying to do
=======================
If you want to do big data analytics there are many solutions out there to do that. For the past couple of weekends I've been playing with some of them, and it looks like I finally found a great tool for my use case: Elasticsearch. A typical question you might want to answer could be: 
__Find the number of members who are between 20 and 30 years old and have borrowed a book of a certain author last month__. 
Here's some criteria for the solution: 

* arbitrary ad-hoc queries should return numbers within a minute
* (linearly) scalable to very large amounts of data 
* update speed not critical

I'm a <a href="https://github.com/mpollmeier/gremlin-scala">graph database enthusiast</a> so I first tried to solve this with <a href="https://github.com/thinkaurelius/titan">Titan</a> (a distributed graph database). However I learned that a graph db <a href="https://groups.google.com/forum/#!topic/aureliusgraphs/lwDP8Eh8z9E">is not the right tool</a> for this particular problem. 

All I needed was something that precomputes indexes and can combine composite indexes in arbitrary queries. So I gave <a href="http://elasticsearch.org">Elasticsearch</a> a try and it seems to work really well for this use case. Elasticsearch let's you persist arbitrary documents and automatically creates indexes on all the fields. It has some nice strategies like nested and parent/child documents that allow it to effectively shard the documents yet allow for powerful searches. E.g. if you define a collection inside your document as a <a href="http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/mapping-nested-type.html">nested type</a>, elasticsearch will index it as a separate entity, yet assure that it's hosted on the same shard as it's parent. By default it creates indexes for all provided fields and it's very efficient for combining indexes. 

Setup with elasticsearch
=======================

Setting up an index and some documents is really nice and easy with it's rest api: 

```json
{
  "member":{
    "name" : {"type": "string", "index": "not_analyzed"},
    "age" : {"type": "integer"},
    "properties":{
      "books": {
        "type": "nested",
        "properties": {
          "author": {"type": "string"},
          "borrowedOn": {"type": "date"}
        }
      }
    }
  }
}
```

* member has an age and a (non-indexed) name
* books is a nested array inside members
* a book has an author and the information when the member borrowed it

For my test case I set up 1 million members and 10 million borrowed books - to make that batch insert go fast I used <a href="http://spray.io">spray</a> to send those http requests. That part itself is quite interesting for learning spray and Akka, so I've included the code in the appendix. A typical member looks like this:

```json
{
  "name": "member 1",
  "age": 25,
  "books": [ 
    {"author": "ranicki", "borrowedOn": "2014-05-17"}, 
    {"author": "klein", "borrowedOn": "2014-04-01"} 
  ]
}
```

Querying Elasticsearch
=======================
Now that everything is setup we are ready to query elasticsearch. Remember we want to find the number of members who are between 20 and 30 years and have borrowed a book written by ranicki in the last month. Here's how you can do that using curl: 

```bash
curl http://localhost:9200/members/member/_search?pretty=true -d '{
  "filter": {
    "and": [
      { 
        "range": { 
          "age": { "gte" : 20, "lt" : 30 } 
        } 
      },
      { 
        "nested": {
          "path": "books",
          "query": {
            "term": { "books.author": "ranicki" }
          }
        }
      },
      { 
        "nested": {
          "path": "books",
          "query": {
            "range": { "books.borrowedOn": { "gt" : "2014-04-18", "lt" : "2014-05-18" } }
          }
        }
      }
    ]
  }
}'
```

And the results come back in around than 100 milliseconds - with cold caches on my three year old laptop! Now that's damn fast! In this case there's about 95k members that hit the criteria. That being said it returns the count of total members and a couple of sample members (optionally the `highest scoring` ones). 
This is the first time I've used Elasticsearch and it's love at first sight! Now let's see if we will actually use this at work... 

Appendix: setup
=======================
<a href="http://www.elasticsearch.org/downloads/">Download</a> elasticsearch and start it with _bin/elasticsearch_

The spray-client code to setup the index, members and books. The full repo is on <a href="https://github.com/mpollmeier/elasticsearch-sprayclient">github</a> if you wanna play yourself. 

```scala
package spray

import java.text.SimpleDateFormat
import java.util.Calendar
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util._

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.routing.RoundRobinRouter
import akka.util.Timeout
import spray.can.Http
import spray.client.pipelining._
import spray.http._

object ElasticSearchTryout extends App {
  import MemberCreator._
  val longTimeout = 300 minutes
  implicit val system = ActorSystem()
  implicit val timeout = Timeout(longTimeout)
  import system.dispatcher

  def await(f: Future[_]) = Await.result(f, longTimeout)

  val pipeline: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup("localhost", port = 9200)
    ) yield sendReceive(connector)

  def setupIndex() = {
    await(pipeline flatMap (_(Delete(s"/members"))))
    await(pipeline flatMap (_(Put(s"/members"))))
    await(pipeline flatMap (_(Post(s"/members/member/_mapping", """
      {
        "member":{
            "name" : {"type": "string", "index": "not_analyzed"},
            "age" : {"type": "integer"},
            "properties":{
              "books": {
                "type": "nested",
                "properties": {
                  "author": {"type": "string"},
                  "borrowedOn": {"type": "date"}
                }
              }
            }
          }
      }"""))))
  }

  setupIndex()
  println("index setup complete")

  val memberCount = 1 * 1000 * 1000
  val memberCreator = system.actorOf(Props(classOf[MemberCreator], pipeline)
    .withRouter(RoundRobinRouter(nrOfInstances = 5)))

  val futures = (0 until memberCount) map { id ⇒ 
    memberCreator ? CreateMember(id)
  }
  Await.ready(Future.sequence(futures), longTimeout)

  println("setting up members complete")
  system.shutdown()
  println("shutdown complete")
}

object MemberCreator {
  case class CreateMember(id: Int)
  case object MemberCreated
}
class MemberCreator(pipeline: Future[SendReceive]) extends Actor {
  import MemberCreator._
  val longTimeout = 30 minutes
  val averageTxCountPerMember = 10
  val rand = new java.util.Random
  implicit val timeout = Timeout(longTimeout)
  import context.dispatcher

  def receive = {
    case CreateMember(id) ⇒ 
      if(id % 1000 == 0) println(s"creating member $id ($self)")
      val respondTo = sender
      Await.ready(createMember(id), longTimeout)
      respondTo ! MemberCreated
  }

  def createMember(id: Int): Future[HttpResponse] = {
    val age = 12 + rand.nextInt(50)
    val bookCount = rand.nextInt(averageTxCountPerMember * 2)
    val books = (0 until bookCount) map createBook
    val request = Put(s"/members/member/$id", s"""
      { 
        "name": "Member $id", 
        "age": $age,
        "books": [ ${books.mkString(",")} ]
      } 
    """)
    pipeline flatMap (_(request))
  }

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
  def createBook(id: Int) = {
    val authors = List("ranicki", "klein", "lessing")
    def randomAuthor(): String = authors(rand.nextInt(authors.size))
    def randomDate(): String = {
      val cal = Calendar.getInstance()
      cal.set(Calendar.YEAR, 2014)
      cal.set(Calendar.DAY_OF_MONTH, 1 + rand.nextInt(28))
      cal.set(Calendar.MONTH, 1 + rand.nextInt(12))
      dateFormat.format(cal.getTime)
    }

    val author = randomAuthor()
    val date = randomDate()
    s"""{ "id": $id, "author": "$author", "borrowedOn": "$date"}"""
  }

  def verifyResponseStatus(response: HttpResponse) = 
    response.status match {
      case StatusCodes.OK | StatusCodes.Created ⇒ //all good
      case _ ⇒ println("problem!", response)
    }
}
```



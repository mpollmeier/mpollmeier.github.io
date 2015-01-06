---
layout: post
title: Calculating the shortest path with Gremlin-Scala
date: 2014-12-27
permalink: 2014/12/27/gremlin-scala-shortest-path
tags: [scala, gremlin, recipe, neo4j]
---

========================

Graph databases are fun - they are fundamentally simple as they only have vertices and edges - yet they are much more powerful than e.g. relational databases for many scenarios.  
[Gremlin](https://github.com/tinkerpop/tinkerpop3) can be seen as the JDBC for graph databases - it allows you to define traversals and has drivers for a large number of graph dbs. The Tinkerpop team has been busy working on the next major version `tinkerpop3`, which is a complete rewrite based on Java 8. They have just released 3.0.0.M6 and are close to the first release candidate. 
I am the maintainer of [Gremlin-Scala](https://github.com/mpollmeier/gremlin-scala), a Scala wrapper to make Gremlin easier to use from Scala. This post uses the latest Gremlin-Scala v3. 

[Gremlin-Scala-Examples](https://github.com/mpollmeier/gremlin-scala-examples) it a collection of sample projects and recipies to get you started as quickly as possible. This article explains one of the recipies - [ShortestPath](https://github.com/mpollmeier/gremlin-scala-examples/blob/master/neo4j/src/test/scala/ShortestPathSpec.scala) - in more detail.

I stole the scenario from my former colleague [Stefan Bleibinhaus](http://bleibinha.us/blog/2013/10/scala-and-graph-databases-with-gremlin-scala) who has done a great job explaining this for an earlier version of Gremlin-Scala (2.5): let's try and find the shortest path between Auckland and Cape Reinga in New Zealand. I live in Auckland and Cape Reinga is quite a popular tourist destination - it's the northernmost point and a very unique place.  

Here's how to setup an example graph in Gremlin-Scala - in this case we are using neo4j, but it would be almost the same with any other graph db. First we model some cities in New Zealand as vertices and the routes between them as edges. The routes carry the distance as a property. 

```scala
  val graph = Neo4jGraph.open("neo4j")
  val gs = GremlinScala(graph)

  def addLocation(name: String): ScalaVertex =
    gs.addVertex().setProperty("name", name)

  def addRoad(from: ScalaVertex, to: ScalaVertex, distance: Int): Unit = {
    // two way road ;)
    from.addEdge(label = "road", to, Map.empty).setProperty("distance", distance)
    to.addEdge(label = "road", from, Map.empty).setProperty("distance", distance)
  }

  val auckland = addLocation("Auckland")
  val whangarei = addLocation("Whangarei")
  val dargaville = addLocation("Dargaville")
  val kaikohe = addLocation("Kaikohe")
  val kerikeri = addLocation("Kerikeri")
  val kaitaia = addLocation("Kaitaia")
  val capeReinga = addLocation("Cape Reinga")

  addRoad(auckland, whangarei, 158)
  addRoad(whangarei, kaikohe, 85)
  addRoad(kaikohe, kaitaia, 82)
  addRoad(kaitaia, capeReinga, 111)
  addRoad(whangarei, kerikeri, 85)
  addRoad(kerikeri, kaitaia, 88)
  addRoad(auckland, dargaville, 175)
  addRoad(dargaville, kaikohe, 77)
  addRoad(kaikohe, kerikeri, 36)
```

Now let's traverse all paths from Auckland to Cape Reinga. We start with a Vertex (Auckland) and follow all outgoing edges (roads) with the `outE` step to the next incoming Vertex (city) using the `inV` step. In the first iteration Gremlin will traverse to Whangarei and Dargaville. 

From there we use the `jump` step to repeat the last two steps (outE and inV) from whatever city Gremlin is in at that point. We pass `jump` a predicate, so that it jumps 5 times max (an arbitrary number) and won't bother jumping back if we either arrived in Cape Reinga or are back in Auckland. 
Then we use the `path` step to get the complete path of the traversal, and `toList` to finally execute the traversal (it wouldn't do anything otherwise, Gremlin is lazy) and return us a List[Path].

```scala
  val paths = auckland.as("a").outE.inV.jump(
    to = "a",
    jumpPredicate = { t: Traverser[Vertex] ⇒
      t.loops < 6 &&
        t.get.value[String]("name") != "Cape Reinga" &&
        t.get.value[String]("name") != "Auckland"
    }
  ).filter(_.value[String]("name") == "Cape Reinga").path.toList
```

Each path holds the list of things Gremlin encountered  while traversing the graph. In our case that's just Vertices (cities) and Edges (roads). For each path we collect the names of the city to get the complete route, for each road we get the distance travelled. That gives us a List of `DescriptionAndDistance` - one entry per path travelled through New Zealand: 

```scala
  case class DescriptionAndDistance(description: String, distance: Int)

  val descriptionAndDistances: List[DescriptionAndDistance] = paths map { p: Path ⇒
    val pathDescription = p.objects collect {
      case v: Vertex ⇒ v.value[String]("name")
    } mkString(" -> ")

    val pathTotalKm = p.objects collect {
      case e: Edge => e.value[Int]("distance")
    } sum
    
    DescriptionAndDistance(pathDescription, pathTotalKm)
  } 
```

To get the shortest path we just need to sort our list by the distance:

```scala
  val shortestPath = descriptionAndDistances.sortBy(_.distance).head
```

And here is the output:

```
found 23 paths from Auckland to Cape Reinga:
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kaitaia -> Cape Reinga,436)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaitaia -> Cape Reinga,442)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kaitaia -> Cape Reinga,445)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kerikeri -> Kaitaia -> Cape Reinga,478)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaikohe -> Kaitaia -> Cape Reinga,472)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kerikeri -> Kaitaia -> Cape Reinga,487)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kerikeri -> Kaikohe -> Kaitaia -> Cape Reinga,508)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Whangarei -> Kaikohe -> Kaitaia -> Cape Reinga,606)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Whangarei -> Kerikeri -> Kaitaia -> Cape Reinga,612)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kaitaia -> Kaikohe -> Kaitaia -> Cape Reinga,600)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kaitaia -> Kerikeri -> Kaitaia -> Cape Reinga,612)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Dargaville -> Kaikohe -> Kaitaia -> Cape Reinga,590)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaikohe -> Kerikeri -> Kaitaia -> Cape Reinga,514)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Whangarei -> Kaikohe -> Kaitaia -> Cape Reinga,606)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Whangarei -> Kerikeri -> Kaitaia -> Cape Reinga,612)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaitaia -> Kaikohe -> Kaitaia -> Cape Reinga,606)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaitaia -> Kerikeri -> Kaitaia -> Cape Reinga,618)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kerikeri -> Kaikohe -> Kaitaia -> Cape Reinga,517)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Whangarei -> Kaikohe -> Kaitaia -> Cape Reinga,615)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Whangarei -> Kerikeri -> Kaitaia -> Cape Reinga,621)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kaitaia -> Kaikohe -> Kaitaia -> Cape Reinga,609)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kaitaia -> Kerikeri -> Kaitaia -> Cape Reinga,621)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Dargaville -> Kaikohe -> Kaitaia -> Cape Reinga,599)

shortest path: DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kaitaia -> Cape Reinga,436)
```

You can find the source code and everything to run it yourself [here](https://github.com/mpollmeier/gremlin-scala-examples/blob/master/neo4j/src/test/scala/ShortestPathSpec.scala). 

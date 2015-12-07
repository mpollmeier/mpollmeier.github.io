---
layout: post
title: Calculating the shortest path with Gremlin-Scala
date: 2014-12-27
permalink: 2014/12/27/gremlin-scala-shortest-path
tags: [scala, gremlin, recipe, neo4j]
---

_Updated 12/12/2015 for Gremlin-Scala 3.1.0-incubating._

Graph databases are fun - they are fundamentally simple as they only have vertices and edges - yet they are much more powerful than e.g. relational databases for many scenarios.
[Gremlin](https://github.com/tinkerpop/tinkerpop3) can be seen as the JDBC for graph databases - it allows you to define traversals and has drivers for a large number of graph dbs. I am the maintainer of [Gremlin-Scala](https://github.com/mpollmeier/gremlin-scala), a Scala wrapper to make Gremlin easier to use from Scala. 

[Gremlin-Scala-Examples](https://github.com/mpollmeier/gremlin-scala-examples) is a collection of sample projects and recipies to get you started as quickly as possible. This article explains one of the recipies - [ShortestPath](https://github.com/mpollmeier/gremlin-scala-examples/blob/master/neo4j/src/test/scala/ShortestPathSpec.scala) - in more detail.

I stole the scenario from my former colleague [Stefan Bleibinhaus](http://bleibinha.us/blog/2013/10/scala-and-graph-databases-with-gremlin-scala) who did a great job explaining this for an earlier version of Gremlin-Scala (2.5): let's try and find the shortest path between Auckland and Cape Reinga in New Zealand. I live in Auckland and Cape Reinga is quite a popular tourist destination - it's the northernmost point and a very unique place.

Here's how to setup an example graph in Gremlin-Scala - in this case we are using neo4j, but it would be almost the same with any other graph db. First we model some cities in New Zealand as vertices and the routes between them as edges. The routes carry the distance as a property. 

```scala
  val graph = Neo4jGraph.open("neo4j").asScala

  val auckland   = graph + (Location, Name → "Auckland")
  val whangarei  = graph + (Location, Name → "Whangarei")
  val dargaville = graph + (Location, Name → "Dargaville")
  val kaikohe    = graph + (Location, Name → "Kaikohe")
  val kerikeri   = graph + (Location, Name → "Kerikeri")
  val kaitaia    = graph + (Location, Name → "Kaitaia")
  val capeReinga = graph + (Location, Name → "Cape Reinga")

  auckland   <-- (Road, Distance → 158) --> whangarei
  whangarei  <-- (Road, Distance →  85) --> kaikohe
  kaikohe    <-- (Road, Distance →  82) --> kaitaia
  kaitaia    <-- (Road, Distance → 111) --> capeReinga
  whangarei  <-- (Road, Distance →  85) --> kerikeri
  kerikeri   <-- (Road, Distance →  88) --> kaitaia
  auckland   <-- (Road, Distance → 175) --> dargaville
  dargaville <-- (Road, Distance →  77) --> kaikohe
  kaikohe    <-- (Road, Distance →  36) --> kerikeri
```

To find the shortest path from Auckland to Cape Reinga we simply start in Auckland and follow all outgoing edges (roads) with the `outE` step and immediately traverse to the next incoming Vertex (city) using the `inV` step. So in the first iteration Gremlin will traverse to Whangarei and Dargaville.

We instruct Gremlin to `repeat` these two steps (`outE.inV`) `until` we reach Cape Reinga. 

Then we use the `path` step to get the complete path of the traversal, and `toList` to finally execute it (Gremlin is lazy). This returns a List[Path].

```scala
  val shortestPath = auckland.asScala
    .repeat(_.outE.inV.simplePath)
    .until(_.is(capeReinga.vertex))
    .path
    .toList
```

We are basically done, all we need is to beauty it up a bit and make sure we find the actual road lengths. Each path holds the list of things Gremlin encountered  while traversing the graph. In our case that's just Vertices (cities) and Edges (roads). For each path we collect the names of the city to get the complete route, for each road we get the distance travelled. That gives us a List of `DescriptionAndDistance` - one entry per path travelled through New Zealand: 

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
time elapsed: 37ms
Paths from Auckland to Cape Reinga:
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kaitaia -> Cape Reinga,436)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaitaia -> Cape Reinga,442)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kaitaia -> Cape Reinga,445)
DescriptionAndDistance(Auckland -> Whangarei -> Kaikohe -> Kerikeri -> Kaitaia -> Cape Reinga,478)
DescriptionAndDistance(Auckland -> Whangarei -> Kerikeri -> Kaikohe -> Kaitaia -> Cape Reinga,472)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Kerikeri -> Kaitaia -> Cape Reinga,487)
DescriptionAndDistance(Auckland -> Dargaville -> Kaikohe -> Whangarei -> Kerikeri -> Kaitaia -> Cape Reinga,621)
```

You can find the source code and everything to run it yourself [here](https://github.com/mpollmeier/gremlin-scala-examples/blob/master/neo4j/src/test/scala/ShortestPathSpec.scala). 

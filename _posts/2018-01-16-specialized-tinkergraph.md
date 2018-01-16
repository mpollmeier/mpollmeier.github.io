---
layout: post
title: Open sourcing our specialized TinkerGraph with 70% memory reduction and strict schema validation
date: 2018-01-16
permalink: 2018/01/16/specialized-tinkergraph
tags: [scala, scalameta]
---
This article was first published on the [ShiftLeft Blog](https://blog.shiftleft.io/open-sourcing-our-specialized-tinkergraph-with-70-memory-reduction-and-strict-schema-validation-fa5cfb3dd82d). 

Most tech companies these days are heavily relying on libre/open-source software, often contributed by volunteers in their spare time. ShiftLeft believes that it is  important to give back by contributing code and artifacts, and today's release is our first milestone in this mission. We are publishing our fork of the open source [Apache TinkerGraph](http://tinkerpop.apache.org/docs/current/reference/#tinkergraph-gremlin) as a standalone artifact. This version of TinkerGraph uses 70% less memory (for our use case, ymmv) and implements a strict schema validation. Since TinkerGraph is a general purpose graph database, chances are you can benefit from this work as well. If not directly, you might still benefit indirectly by learning from our learnings.

Some context: ShiftLeft finds potential security vulnerabilities and data leaks using static code analysis, generating a security profile for your application and your custom profile, that is then enforced at [runtime](https://blog.shiftleft.io/how-shiftleft-enhances-java-security-b99ea1fa6723). During static code analysis we construct and traverse a [code property graph](https://blog.shiftleft.io/semantic-code-property-graphs-and-security-profiles-b3b5933517c1). A `code property graph` is a specialized version of a `property graph`, which is simply defined as a collection of vertices (a.k.a. nodes) that are connected by edges (a.k.a. relationships), both of which can have properties. Our domain is `code`, hence the name. Here's a (very small) subset of the code property graph schema. 
![code property graph](images/codepropertygraph.png)

To implement our static code analysis, we chose [Apache Tinkerpop](http://tinkerpop.apache.org), a widely used general purpose graph traversal stack. Tinkerpop aims to be the JDBC of graph databases: it abstracts over common graph database primitives and allows database vendors to provide driver implementations. The [list of database drivers](http://tinkerpop.apache.org/providers.html#data-system-providers) that support the stack is impressive, however the quality of the implementation varies, mostly depending on the effort the vendor and/or it's userbase are willing to put in. Tinkerpop also provides a reference implementation of an in memory graph database called [TinkerGraph](http://tinkerpop.apache.org/docs/current/reference/#tinkergraph-gremlin). It's designed for simplicity, so that other vendors can use it as an archetype for their driver implementations. It does implement a large set of features though. We figured that, for our use cases, TinkerGraph is actually sufficient. Also, it's widely used (mostly for testing/prototyping though) and simple to reason about, so we decided to start with TinkerGraph and only switch to a more specialized database as need arises.

We were very happy with that decision, until we started to analyze bigger code bases and started hitting the limits of our workstations, mostly regarding the memory usage. Naturally we thought: ok, maybe it's time to move on to a more *advanced* graph database. So we tried out a few alternatives, but each of these came with downsides - mostly due to their distributed nature and/or the maturity of the driver implementation. Since TinkerGraph is designed for simplicity (i.e., not for memory efficiency) and is open source under the permissive Apache2 License, we saw the opportunity to fix this ourselves.

## Finding an angle to improve the memory footprint

First we wanted to get an overview of where the memory is spent for a typical code property graph. So we fired up [VisualVM](https://github.com/oracle/visualvm) to investigate the allocated heap memory and quickly eyed `HashMap` and `HashMap$Node` as potential candidates. These HashMaps looked promising because they are used for generic data structures, which we (and maybe you?) don't actually need, since we know exactly how our graph is structured. 

Let me explain by diving a little deeper. These are the main use cases for HashMaps in TinkerGraph:

1) Allow any vertex and any edge to have any property (basically a key/value pair, e.g., `foo=42`). To achieve this, each element in the graph has a `Map<String, Property>`, and each property is wrapped inside a `HashMap$Node`. (See [TinkerVertex](https://github.com/apache/tinkerpop/blob/3.3.0/tinkergraph-gremlin/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerVertex.java#L45) and [TinkerEdge](https://github.com/apache/tinkerpop/blob/3.3.0/tinkergraph-gremlin/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerEdge.java#L43).)

2) TinkerGraph allows to connect any two vertices by any edge. Therefor each vertex holds two `Map<String, Set<Edge>>` instances (one for incoming and one for outgoing edges), where the String refers to the edge label.

Being generic and not enforcing a schema makes complete sense for the default TinkerGraph - it allows users to play without restrictions and build prototypes. However, once a project is more mature, chances are you have a good understanding of your domain and can define a schema so that you don't need the generic structure any more and can save a lot of memory by getting rid of it.

Some quick math: given a graph with 1M vertices and 10M edges and an average of five properties per element, this results in roughly 13M `HashMap` instances and 65M `HashMap$Node` instances, summing up to about 3G allocated heap (the exact number depends on your platform and JVM). Note that this comes *on top* of the actual data - it is just for the generic structure: allowing to define any property on any element, and connecting any two vertices with any edge. 

## Two birds with one stone: enforcing a strict schema

Using less memory is not the only benefit, though. Knowing exactly which properties a given element can have, of which type they are and which edges are allowed on a specific vertex, helps catch errors early in the development cycle. Your IDE can help you to build valid (i.e., schema conforming) graphs and traversals. If you use a statically-checked language, your compiler can find errors that would otherwise only occur at runtime. Even if you are using a dynamic language you are better off because you'll get an error when you load the graph, e.g., by setting a property on the wrong vertex type. This is far better than getting invalid results at query time, when you need to debug all the way back to a potentially very simple mistake. Since we already had a loosely-defined schema for our code property graph, this exercise helped to complete and strengthen it.

## What does this mean in practice?

"Enforcing a strict schema" actually translates to something very simple: we just replaced the *generic* HashMaps with *specific* members:

1) Element properties: vertices and edges contain *generic* `HashMap<String, Object>` that hold all the element's properties. We just replaced them with *specific* class members, e.g., `String name` and `String return_type`.

2) Edges on a vertex: the *generic* TinkerVertex contains two `HashMap<String, Set<Edge>> in|outEdges` which can reference any edge. We replaced these by *specific* `Set<SomeSpecificEdgeType>` for each edge type that is allowed to connect this vertex with another vertex.

The result is that we can throw an error if the schema is violated, e.g., if a the user tries to set a property that is not defined for a specific vertex, or if the user tris to connect a vertex via an edge that's not supposed to be connected to this vertex. It is important to note, however, that it's up to you if you want to make this a strict validation or not - you can choose to tolerate schema violations in your domain classes.

## Results: 70% less memory, slightly faster traversals

Saving a few HashMaps here and there sounds like a marginal change, but since this affects every single element in the graph, the savings quickly accumulate. Loading a medium sized code property graph into the official (generic) TinkerGraph consumes 1880M heap memory alone. When replacing that with our specialized version it only needs 550M, which is a 71% decrease in memory usage. The savings may be smaller or larger depending on your domain. Generally speaking, the more elements you have in the graph, the more you save. If you only have a few elements with millions of properties, there won't be a big difference. It's more common to have millions of elements with only a few properties each, in which case the savings are big. The time to traverse the graph is faster in some cases (up to 40% for some specific traversals), and about the same for others. The numbers depend on kind of mix of traversals you do, obviously. Overall we experienced a 15% performance improvement, which is great, especially since it wasn't the main goal. 

## Usage

Our specialized version of TinkerGraph is published to [maven central](https://maven-badges.herokuapp.com/maven-central/io.shiftleft/tinkergraph-gremlin). By default, nothing is different from the official TinkerGraph. If you wish to use the strict schema, making use of the memory optimization discussed above, you need to pass lists of [`SpecializedElementFactory.ForVertex`](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/blob/f26ce90cde/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/SpecializedElementFactory.java#L29) and [`SpecializedElementFactory.ForEdge`](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/blob/f26ce90cde/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/SpecializedElementFactory.java#L34) to [`TinkerGraph.open`](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/blob/f26ce90cde/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerGraph.java#L153-L156). These Factories are specific to your domain. Their role is to create instances of your domain classes from a `Map<String, Object>` of key/values (properties). Your domain classes must extend [SpecializedTinkerVertex](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/blob/f26ce90cde/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/SpecializedTinkerVertex.java) for vertices and [SpecializedTinkerEdge](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/blob/f26ce90cde/src/main/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/SpecializedTinkerEdge.java) for edges. To help you get started, the repository contains examples for the [grateful dead graph](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/tree/master/src/test/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/specialized/gratefuldead) and there is a [full test setup](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin/blob/f26ce90cde/src/test/java/org/apache/tinkerpop/gremlin/tinkergraph/structure/SpecializedElementsTest.java#L41-L51) that uses them.

You might want to start by manually writing your specialized domain vertices and edges. Since they are straightforward and boilerplate, we quickly moved on to automatically generating them from our schema. Other than that, it's a minimally invasive operation, because all other graph and traversal APIs remain the same, i.e., you won't need to change any of your queries. We didn't encounter a single issue when we deployed this into production. 

## Conclusion

This effort is pushing the boundaries of local computation with graphs. In times where a lot of focus and energy is spent on distributed computation (for good reasons), it is easy to forget that distributed systems also have disadvantages, one of them being that they add complexity. At ShiftLeft we make use of distributed computation where it's beneficial, but we also believe that simplicity is too often overlooked when choosing the best tool for the job.
When we started this we simply wanted to solve our specific problems, and we did: 70% less memory usage, 15% faster traversals and strict schema validation, all with minimal changes. Then we realized that others can benefit from this as well, hence this article announcing the publication of our specialized TinkerGraph. And maybe, just maybe, others (I'm looking at you, dear reader) may find more ways to improve this and everybody benefits again - the beauty of open source! Did I mention that we are hiring, and that we love candidates with a passion for open source?

## References

* [Sources on github](https://github.com/ShiftLeftSecurity/tinkergraph-gremlin) 
* [Build and deploy on travis.ci](https://travis-ci.org/ShiftLeftSecurity/tinkergraph-gremlin)

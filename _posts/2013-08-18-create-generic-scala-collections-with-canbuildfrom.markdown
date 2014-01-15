---
layout: post
title: Create generic Scala collections with CanBuildFrom
permalink: create-generic-scala-collections-with-canbuildfrom
tags: [scala]
---
Normally if you write a function that takes a collection and returns a new collection that you create inside the function, you need to specify the concrete type of the collection. Otherwise, how are you going to create it? For example if  you could do <em>val result = Seq(1,2,3)</em>, then your function returns either a Seq or an Iterable (the super type of all collections). But what if you want the caller of your function to determine which collection type to return? I.e. if I call that method with a Seq, I want it to return a Seq. If I call it with a List, it should create and return another List. 

The functions inside collection do that already. Take <em>map</em> for example: Seq[A].map(A ⇒ B) returns a Seq[B], while List[A].map(A ⇒ B) returns a List[B]. That means that inside the map function it creates a new instance of the collection type we called it on. 
How is that magic implemented? One could think that reflection might help here, but that gets us in all sorts of trouble: it's slow, suffers from type erasure etc. Nope, in this case it's actually implemented using an implicit value that's every collection has in scope: <strong>CanBuildFrom</strong>. 

This is a really nice use case for implicits, as it allows us exactly what we wanted - create and fill an arbitrary collection - and is absolutely transparent for the caller. CanBuildFrom is basically a factory for any given collection that the compiler will fill in automatically for us. 

While you can easily see how it's used within collection types itself, most of us will not define new or extend existing collection types. So how does it work if we want to use it outside of a collection implementation? The below reimplements <em>map</em> outside of a collection. I.e. map takes an arbitrary collection C[A] and a function that turns A ⇒ B. It creates an empty instance of the collection C[B] and fills it by calling the function A ⇒ B for each element. 

``` scala
/**
 * Demonstrates usage of CanBuildFrom 
 * by reimplementing map outside of a collection.
 * Takes a collection C[A] and a function A ⇒ B
 * Returns a colleciton C[B]
 */
def map[A, B, C[A] <: Iterable[A]](collection: C[A])(f: A ⇒ B)(
  implicit cbf: CanBuildFrom[C[B], B, C[B]]): C[B] = {
  val builder = cbf()
  builder.sizeHint(collection.size)
  collection foreach { x ⇒ builder += f(x) }
  builder.result
}

map(Seq(1,2)) { _.toString } //returns Seq[String]
map(List(1,2)) { _.toString } //returns List[String]
```

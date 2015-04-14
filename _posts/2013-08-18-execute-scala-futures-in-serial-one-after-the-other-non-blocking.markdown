---
layout: post
title: Execute Scala Futures in serial one after the other (non-blocking)
permalink: execute-scala-futures-in-serial-one-after-the-other-non-blocking
tags: [scala]
---
Scala's Future implementation is really cool, it's super easy to execute code in parallel, plus it's very composable because it is a monad. As soon as you create a Future, it wanders off into a separate Thread and you eventually get the result: 

```scala
val list = List(1,2)
def doubleFuture(i: Int) = Future { i * 2 }

list map doubleFuture //returns List[Future[Int]]
Future.traverse(list)(doubleFuture) //returns Future[List[Int]]
```

If we just <em>map</em> the list using our function, we get a list of Futures. More useful in this situation is to use <em>Future.traverse</em>, which essentially does the same but only returns a single Future. That one completes when all computations have been finished. 
Please note that both statements return instantly and execute all futures in parallel (that's obviously a simplification as it depends on your Executor etc.). 

Sometimes if you have a cool new tool, new problems arise. Maybe you don't want to execute everything in parallel every time? And maybe you want to stop the whole process if one of the computations failed? At work we had a couple of cases where we wanted to do exactly that: execute the futures one after the other, and stop if one fails. Obviously we did not want to use Await.result as that would block a precious thread. 
The trick is to fold over the list and use a for comprehension (alternatively flatMap) inside the fold, so that we only compute the next value once the previous has completed:

```scala
def serialiseFutures[A, B](l: Iterable[A])(fn: A ⇒ Future[B])
  (implicit ec: ExecutionContext): Future[List[B]] =
  l.foldLeft(Future(List.empty[B])) {
    (previousFuture, next) ⇒
      for {
        previousResults ← previousFuture
        next ← fn(next)
      } yield previousResults :+ next
  }
```

The fold passes the result of the previous computation into the current one (<em>previousFuture</em>). Using a for comprehension we only call our function once the previous future is complete (and successful). This also returns immediately, but the futures are actually executed one after the other - in serial.

While that does the trick, there's one downside to it: the return type is Future[List[B]]. Ideally it should return Future[C[B]] where C is the type of the collection we passed in. If you've read my <a href="http://www.michaelpollmeier.com/create-generic-scala-collections-with-canbuildfrom/">previous post</a> on CanBuildFrom then you know how you can do that:

```scala
def serialiseFutures[A, B, C[A] <: Iterable[A]]
  (collection: C[A])(fn: A ⇒ Future[B])(
  implicit ec: ExecutionContext,
  cbf: CanBuildFrom[C[B], B, C[B]]): Future[C[B]] = {
  val builder = cbf()
  builder.sizeHint(collection.size)

  collection.foldLeft(Future(builder)) {
    (previousFuture, next) ⇒
      for {
        previousResults ← previousFuture
        next ← fn(next)
      } yield previousResults += next
  } map { builder ⇒ builder.result }
}
```

And here's the ScalaTest code for it:

```scala
val start = System.currentTimeMillis
val doubled = Await.result({
  serialiseFutures(List(10, 20)) { i ⇒
    Future {
      Thread.sleep(i)
      i * 2
    }
  }
}, 1 second)

val timeElapsed = System.currentTimeMillis - start
timeElapsed should be >= (30l)
doubled should be(List(20, 40))
```

Questions
=======================
Q: Zoran Perosevic on 14/04: What is expected behavior in case of exceptions? Would the execution of subsequent futures stop if one throws an exception? 

A: In this implementation it would stop the execution if one future fails. That's because of the nature of the for comprehension (which is using flatMap).
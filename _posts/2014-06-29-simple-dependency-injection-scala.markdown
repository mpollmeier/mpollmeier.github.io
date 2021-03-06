---
layout: post
title: Super simple dependency injection in Scala
date: 2014-06-29
permalink: 2014/06/29/simple-dependency-injection-scala
tags: [scala, di, dependency injection]
---

tl;dr: this simple pattern gives you 90% of the benefits of DI, without the overhead of DI:

```scala
trait MyService {
  def foo = 42
}
object MyService extends MyService

class MyClient {
  val service: MyService = MyService
}
```

### The full story

There are plenty of good reasons for <a href="https://en.wikipedia.org/wiki/Dependency_injection">Dependency injection (DI)</a>, and there's different styles of doing it in Scala. A good overview can be found [here](https://di-in-scala.github.io/). However all styles have some overhead - so if you don't find one you like, I suggest to go crazy and go without DI. 

Hang on, that sounds like blasphemy!? Great!

The main use case for DI is testability: if you want to test an implementation that depends on a service (e.g. a database) you can mock out the database and test your implementation in isolation. Another one is reusing components in other modules. 

In the Scala ecosystem there are a couple of options to do this. The cake pattern uses Scala's mix in composition to achieve DI at compile time, however it is quite boilerplatey and increases compile times quite dramatically. 

Some projects use standard Java frameworks like Spring or Guice that do the DI at runtime - especially those that migrated from Java to Scala. One problem with this is that you have mutable state (vars) in your codebase, which is against all FP principles - e.g. because it makes reasoning about your application much harder. Another (IMHO) more annoying problem is that these containers take a long time to start up, which sucks when you write in a TDD style. And this ramp-up time gets longer and longer as your application grows. It also increases the complexity of your application.

Another option is to use <a href="http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di/">structural typing</a>: have a master config object somewhere and hand it into every component. This feels very boilerplatey, too. 

Remember the main use case is being able to override certain services with mock objects or alternative implementations. We can get this easily with vanilla Scala which is fast, immutable and easy to reason about. If you've written some Scala this will be very familiar:

```scala
trait MyService {
  def foo = 42
}
object MyService extends MyService

class MyClient {
  val service: MyService = MyService
}
```

In your test code:

```scala
val mockService = mock[MyService]
val client = new MyClient { override val service = mockService }
```

Analogously you can have a different implementation of MyService if you want to reuse MyClient in a different part of your application. In my experience 98% of the components are used the same way, so you only have to deal with the remaining 2%, and it's very straightforward:

```scala
trait MyOtherService extends MyService {...}
object MyOtherService extends MyOtherService

val client = new MyClient { override val service = MyOtherService }
```

The idea is the same as e.g. in Spring: we instantiate a singleton object of MyService and 'inject' it into MyClient. And that's it. It's so simple, first I found it too straightforward to even blog about, but as more developers are joining the Scala ecosystem I think it's good to mention that there are alternatives to full blown DI frameworks. At work we've used this simple pattern for a few months now and haven't looked back so far. 


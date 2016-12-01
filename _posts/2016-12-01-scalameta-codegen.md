---
layout: post
title: Introduction to code generation with scalameta
date: 2016-12-01
permalink: 2016/12/01/scalameta-code-generation-tutorial
tags: [scala, scalameta]
---

Metaprogramming in Scala started with macros which have been experimental from the beginning. Macros are powerful, but very hard to reason about and to debug. For these and more reasons macros will disappear from Scala, however that's only half true since they'll be replaced by the new `inline / meta` construct from scalameta. Scalameta promises to have all the good stuff that macros have, yet is easier to reason about and debug. That was reason enough for me to have a sneak peak at what's available right now. It turned out that the functionality feels almost complete! I did notice a lack of introductory documentation / tuturials though, so this article is what I wish was there when I got started. 

One use case for scalameta is to parse and analyse code as done e.g. in [scalafmt](https://github.com/olafurpg/scalafmt/). That area already has some good entry level tuturials mostly by Ólafur Páll Geirsson, see e.g. [A Whirlwind Tour of scala.meta](http://scalameta.org/tutorial/) with [companion video](https://www.youtube.com/watch?v=-l7pV0sFq1c) and [Three cool things you can do with scala.meta](https://geirsson.com/post/2016/02/scalameta/). 

Another (IMO even more important) use case is code generation, i.e. doing code introspection at compile time and transform it or add more code to it. The first thing that comes to (my) mind with metaprogramming is to build a serialiser for case classes, so let's do just that! We'll take baby steps, and there's a companion [repo](https://github.com/mpollmeier/scalameta-tutorial) that I encourage you to clone which has every step we do in a separate commit so you can run, debug and modify it yourself. 

## Goal: additional function `toMap`
We will create an annotation `@mappable` that can be applied to any case class. During compilation we want scalameta to add a new function `toMap` to our case class and (for now) just return an empty map. Code is always more concise than words, so here's how we want it to work:

```scala
@mappable case class SimpleCaseClass(i: Int, s: String)
val testInstance = SimpleCaseClass(i = 42, s = "something")
testInstance.toMap shouldBe Map.empty[String, Any]
```

## Step 1: create an annotation and use inline/meta
Here's how to define the annotation and invoke scalameta using the new `inline` / `meta` style:

```scala
class mappable extends StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    ...
  }
}
```

The function `inline def apply(defn: Any) = meta {...}` will be invoked at compile time, i.e. whatever you define inside `meta` is scala code that runs at compile time. You can introspect the internals of the case class, influence the result of the compilation etc and even do useful stuff like `println` - the sky is the limit :) 
The parameter `defn` is the complete definition of the case class in form of the scalameta DSL (`scala.meta.Defn` or simlar, defined in [Trees.scala](https://github.com/scalameta/scalameta/blob/master/scalameta/trees/src/main/scala/scala/meta/Trees.scala)), and `apply` will also return a `scala.meta.Defn` or similar.

## Step 2: create a new function `toMap` and put everything together
So now we will finally do some metaprogramming to add the function `toMap`. First we deconstruct the case class definition `defn` using the funny looking `q"..."` thing called [quasiquotes](https://github.com/scalameta/scalameta/blob/master/notes/quasiquotes.md). You might know them from the old style macros - if not just think of them like matching a case class and extracting the parameters at the same time. The special thing here is that after that first line we'll have the values `mods`, `tName`, `params` and `template` in scope.

```scala
  val q"..$mods class $tName (..$params) extends $template" = defn
  println(tName) //this actually prints during compilation
```

You might have noticed that e.g. `params` is prefixed with two dots. That is the quasiquote way to indicate that we are expecting a `Seq[Param]`. Now we can construct the result, i.e. the same case class as was defined, but with the additional `toMap` function. We use quasiquotes again for that.

```scala
  q"""
    ..$mods class $tName(..$params) {
      def toMap(): Map[String, Any] = Map.empty[String, Any]
    }
  """
```

And that's it! The return type of our quasiquote above is again a scala.meta.Defn.Class, just like our input `defn`. 
I encourage you to clone the [companion repo](https://github.com/mpollmeier/scalameta-tutorial), check out [commit 141b544](https://github.com/mpollmeier/scalameta-tutorial/commit/141b544) and have a play around (sbt command: `;clean;examples/clean;examples/test`). 

## Step 2: implement `toMap` properly
It's probably time to do something actually useful now, so let's implement `toMap` so that it returns a map with all case class members. The test we want it to pass looks like this:

```scala
@mappable case class SimpleCaseClass(i: Int, s: String)
val testInstance = SimpleCaseClass(i = 42, s = "something")
testInstance.toMap shouldBe Map("i" -> 42, "s" -> "something")
```

Here is the complete content of the meta block that achieves what we want:

```scala 
val q"..$mods class $tName (..$params) extends $template" = defn

val keyValues: Seq[Term] = params.map { param =>
  val memberName = Term.Name(param.name.value)
  q"${param.name.value} -> $memberName"
}

q"""
  ..$mods class $tName(..$params) {
    def toMap(): Map[String, Any] = Map[String, Any](..$keyValues)
  }
"""
```

Compared to before there is only one change: instead of returning an empty map, we now construct a `Map[String, Any]` that contains the members of our case class. For our use case above the generated code will be `Map[String, Any]("i" -> i, "s" -> s)` - you'll see exactly that if you print out the result of the quasiquote. 

To construct the tuples `keyValues` that we pass to the map constructor, we take the case class `params` that we extracted from `defn` in the first line. In our use case that's `Seq(i: Int, s: String)`. We now have all we need to transform that to `Seq(("i" -> i), ("s" -> s))` using another quasiquote (they are composable!). 

You might notice again that `$keyValues` is prefixed with two dots, that's of course again because it's a `Seq`. Pro tip: Seq[Seq] uses three dots :)

Again, please clone the [companion repo](https://github.com/mpollmeier/scalameta-tutorial) and play around - this step is [commit 9d06bc3](https://github.com/mpollmeier/scalameta-tutorial/commit/9d06bc3). 

# A few postscripts
Just like with old style macros, the scalameta code needs to be defined in a separate compilation unit so that they get compiled first and can then be invoked while compiling the other sources. That's easily setup in sbt as demonstrated in the companion repository.

To debug scalameta code I typically just use `println` - we're 21st century developers after all! I hear IDE support is ramping up for scalameta as well though. 

As good first read is the official [scalameta page](http://scalameta.org/).

You'll find it much easier to understand if you familiarise youself with the scalameta DSL - the majority of the ADTs are defined in [Trees.scala](https://github.com/scalameta/scalameta/blob/master/scalameta/trees/src/main/scala/scala/meta/Trees.scala). 

Introductory talk 'Metaprogramming 2.0' by Eugene Burmako: [video](https://www.youtube.com/watch?v=wii5UPtu1_g) and [slides](http://scalamacros.org/paperstalks/2016-05-11-Metaprogramming20.pdf). 

Scalameta quasiquotes are defined [here](https://github.com/scalameta/scalameta/blob/master/notes/quasiquotes.md). 

I took this exercise seriously and created [scalameta-serialiser](https://github.com/mpollmeier/scalameta-serialiser) which also has `fromMap` and defines both in the companion object. The library is published on maven central, so it's easy to use :)

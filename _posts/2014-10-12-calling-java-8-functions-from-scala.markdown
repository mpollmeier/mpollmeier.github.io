---
layout: post
title: Calling Java 8 functions from Scala
date: 2014-10-12
permalink: 2014/10/12/calling-java-8-functions-from-scala
tags: [scala, java, interop]
---

Java 8 made a step towards functional programming by providing it's own lambda syntax and a whole set of classes in the `java.util.function` package. I expect future versions of Scala to have a better interoperability with Java 8, but for now if you want to call Java 8 code that expects Java 8 functions you could use the following (non-complete) list of implicit conversions. You write a normal Scala function and they convert it to a Java 8 Function, Predicate, BiPredicate etc.. 

I wrote them for [Gremlin-Scala](https://github.com/mpollmeier/gremlin-scala) and thought it might be worth sharing. I put these implicit conversions into the [package object](https://github.com/mpollmeier/gremlin-scala/blob/e091246e33a711316634c0b99d2b29aa526c284a/src/main/scala/com/tinkerpop/gremlin/package.scala).

```scala
import java.util.function.{ Function ⇒ JFunction, Predicate ⇒ JPredicate, BiPredicate }

//usage example: `i: Int ⇒ 42`
implicit def toJavaFunction[A, B](f: Function1[A, B]) = new JFunction[A, B] {
  override def apply(a: A): B = f(a)
}

//usage example: `i: Int ⇒ true`
implicit def toJavaPredicate[A](f: Function1[A, Boolean]) = new JPredicate[A] {
  override def test(a: A): Boolean = f(a)
}

//usage example: `(i: Int, s: String) ⇒ true`
implicit def toJavaBiPredicate[A, B](predicate: (A, B) ⇒ Boolean) =
  new BiPredicate[A, B] {
    def test(a: A, b: B) = predicate(a, b)
  }

```

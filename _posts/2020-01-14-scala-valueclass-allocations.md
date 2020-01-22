---
layout: post
title: Does my Scala Value Class really not get allocated?
date: 2020-01-14
permalink: 2020/01/14/scala-valueclass-allocations
tags: [scala]
---

Scala's Value Classes are a powerful and widely used mechanism for extension methods and additional type safety, both without incurring the runtime overhead of an allocation.

For example, we can provide additional methods for instances of `Foo` (which may well be defined in a library out of our control) as follows:

```scala
class Foo

implicit class FooExt(val foo: Foo) extends AnyVal {
  def bar = 42
}

// usage
val foo = new Foo
foo.bar // will not allocate FooExt
```

There are more examples in [Scala's documentation](https://docs.scala-lang.org/overviews/core/value-classes.html), which (together with [AnyVal's scaladoc](https://www.scala-lang.org/api/current/scala/AnyVal.html)) documents the limitations of this mechanism. In other words, the compiler can only avoid allocations, if all of the following are true for a given Value Class:

* must have only a primary constructor with exactly one public parameter whose type is not a user-defined value class
* can define defs, but no vals, vars, or nested traits, classes or objects
* may not have @specialized type parameters
* may not have nested or local classes, traits, or objects
* may not define a equals or hashCode method.
* must be a top-level class or a member of a statically accessible object
* cannot be extended by another class

Unfortunately it's not straightforward to see if the compiler really succeeded in avoiding the allocation. An annotation or compiler warning similar to `@tailrec` would be nice and there's a [long standing ticket](https://github.com/scala/bug/issues/9504) on this. But it looks like we'll have to wait for Scala 3 for this. 

The above example is rather straightforward and can be verified by hand, but most use cases are more complicated. Also, there are alternative ways to achieve the same, e.g. `implicit def` instead of `implicit class`:
```scala
implicit def toFooExt(foo: Foo) = new FooExt(foo)
```
There's an explicit allocation of `FooExt` here - does the compiler still optimize it out? (The answer is _yes_.)

And how about if we use an `implicit class` but no `AnyVal`? (Interestingly, this also does not incur an allocation at runtime):
```scala
implicit class FooExtImplicitClass(val foo: Foo) {
  def bar = 42
}
```

Back to the question: how can we verify if all the conditions are met and the compiler successfully optimized our code, so that there won't be an allocation at runtime?
The easiest and safest way I found was to decompile the `.class` files back to java, and search for the `new` keyword. In absence of a good decompiler (like [cfr](http://www.benf.org/other/cfr/)) you can also use `javap -c`. Here is a complete and copy-pasteable example with four alternative use cases:

ValueClassDebug.scala:
```scala
object ValueClassDebug {
  class Foo

  class FooExtNormalClass(val foo: Foo) {
    def bar: Int = 42
  }

  implicit class FooExtImplicitClass(val foo: Foo) {
    def barViaImplicitClass: Int = 42
  }

  implicit class FooExtImplicitValueClass(val foo: Foo) extends AnyVal {
    def barViaImplicitValueClass: Int = 42
  }

  implicit def toFooExt(foo: Foo) = new FooExtAnyVal(foo)
  class FooExtAnyVal(val foo: Foo) extends AnyVal {
    def barViaImplicitDef: Int = 42
  }
}

object Main extends App {
  import ValueClassDebug._
  val foo = new Foo
  
  // explicit instantiation of normal class always results in object allocation
  // new Ext.FooExtNormalClass(this.foo()).bar();
  new FooExtNormalClass(foo).bar 

  // no object allocation when using implicit class (note: no value class)
  // Ext$.MODULE$.FooExtImplicitClass(this.foo()).barViaImplicitClass();
  foo.barViaImplicitClass

  // no object allocation when using implicit value class
  // Ext.FooExtImplicitValueClass$.MODULE$.barViaImplicitValueClass$extension(Ext$.MODULE$.FooExtImplicitValueClass(this.foo()));
  foo.barViaImplicitValueClass

  // no object allocation when using implicit function
  // Ext.FooExtAnyVal$.MODULE$.barViaImplicitDef$extension(Ext$.MODULE$.toFooExt(this.foo()));
  foo.barViaImplicitDef
}
```

```bash
scalac ValueClassDebug.scala
cfr Main\$.class #any other java decompiler will do, alternatively `javap -c`
```

Thanks to Denis Yermakov for pointing me in the right direction. 

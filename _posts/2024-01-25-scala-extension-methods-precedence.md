---
layout: post
title: Scala extension methods vs implicits - beware of precendence
date: 2024-01-25
permalink: 2024/01/scala-extension-methods-precedence
tags: [scala]
---

Scala extension methods vs implicits: beware of precendence

I just noticed one important difference between extension methods and old school implicits in Scala 3: the former have a higher precedence. If that's not what you wanted, you be better off with plain old implicits. Canonical example for the scala repl:

```scala
type A
object Foo {
  extension (a: A) {
    def hello = "in extension"
  }
}
import Foo.*
val hello = "pure value"
// <evaluate the above, and then:

hello
// val res0: A => String = Lambda$1367/0x0000000800571c08@71eff6a3
// ^ extension method takes precedence over value
```

Using old school implicits:
```scala
type A
object Foo {
  class Ext(a: A) extends AnyVal {
    def hello = "in extension"
  }
  implicit def toExt(a: A): Ext = new Ext(a)
}
import Foo.*
val hello = "pure value"
// <evaluate the above, and then:


hello
// val res0: String = pure value
// ^ pure value takes precedence over implicit
```

Note: without the surrounding `object Foo` and the import, the pure value has precedence even with extension methods

```scala
type A
extension (a: A) {
  def hello = "in extension"
}
val hello = "pure value"
// <evaluate the above, and then:


hello
// val res0: String = pure value
// ^ pure value takes precedence over implicit
```

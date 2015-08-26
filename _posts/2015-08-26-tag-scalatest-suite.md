---
layout: post
title: Tag a whole ScalaTest suite (update for Java 8)
date: 2015-08-26
permalink: 2015/08/26/tag-scalatest-suite
tags: [scala, java8, recipe, test]
---

In ScalaTest you can [tag individual tests](http://www.scalatest.org/user_guide/tagging_your_tests) quite easily, like this:

`"it does this and that" taggedAs(Tag("requiresDb")) in { ... }`

What's not so well documented is how you can tag a whole suite of tests. If you are writing a suite of database tests, it gets very repetitive to add the `taggesAs` to every test, and it's easy to forget aswell. Two years ago Krzysztof Ciesielski [blogged](https://abstractionextraction.wordpress.com/2013/09/18/easy-suite-tagging-with-scalatest-2-0/) about how to tag a whole suite using a java annotation. Here's an updated version that works with Java 8:

src/test/java/tags/RequiresDb.java:

```java
package tags;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

@org.scalatest.TagAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresDb {}
```

src/test/scala/MyBusinessTest.scala:

```scala
import org.scalatest.{ Matchers, WordSpec }

@tags.RequiresDb
class MyBusinessTest extends WordSpec with Matchers {
  "loads business objects from db" in { ... }
```

Usage from sbt:

* `testOnly * -- -n tags.RequiresDb`  //exclusively run all suites tagged with RequiresDb
* `testOnly * -- -l tags.RequiresDb`  //exclude all suites tagged with RequiresDb


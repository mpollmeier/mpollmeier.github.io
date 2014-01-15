---
layout: post
title: Fun with Scala's new Reflection API (2.10)
date: 2013-01-27 03:59:49.000000000 +01:00
permalink: fun-with-scalas-new-reflection-api-2-10
tags: [scala, reflection]
---
Scala 2.10 introduced a new reflection API (<a href="http://docs.scala-lang.org/overviews/reflection/typetags-manifests.html">see here for an in-depth overview</a>) which is much closer to the actual type system than the old Manifests were - <a href="http://stackoverflow.com/questions/10513336/how-do-the-new-scala-typetags-improve-the-deprecated-manifests">in fact</a> the compiler uses TypeTags itself. And it's not hard to use as we'll see. One reason why Scala provides reflection additional to Java's reflection API is that the JVM erases generic types. As a motivation let's see how generic types get erased at runtime:

```scala
  case class A[T]
  println(A[String].isInstanceOf[A[String]]) // true
  println(A[String].isInstanceOf[A[Int]])    
  // true aswell - doesn't sounds right, no? 
  // that's because the type of T gets erased at runtime...
```

Scala 2.10 introduces TypeTag which brings us the compile-time information about the generic type T to the runtime:

```scala
  import scala.reflect.runtime.universe._
    case class B[T: TypeTag] {
      val tpe = typeOf[T]
  }
  println(B[String].tpe == typeOf[String]) // true
  println(B[String].tpe == typeOf[Int])    // false

  //and this also works for nested parameterised types:
  println(B[List[String]].tpe == typeOf[List[String]]) //true
  println(B[List[String]].tpe == typeOf[List[Int]])    //false
```

Another use case for the reflection API we just had at <a href="http://www.movio.co/">Movio</a> is to get all members of an object that are of a given type. We define a customer specific configuration in objects that drives our whole system - from the <a href="http://petstore.swagger.wordnik.com/">Swagger API documentation</a> all the way down to how we persist the data in <a href="http://cassandra.apache.org/">Cassandra</a> and do our filters. The code below grabs all members of an object that are subtypes of Field, gets their generic type T and checks if they mix in a given trait `Required`. Scala's runtime mirrors allow us to lookup the symbols and types for a given instance at runtime - pretty neat!

```scala
  import scala.reflect.runtime.universe._
  case class Field[T: TypeTag] {
    val tpe = typeOf[T]
  }
  trait Required //can be mixed into Field to mark it as required
  
  object SomeClass extends BaseClass {
    val stringField = Field[String]
    val requiredIntField = new Field[Int] with Required
  }

  abstract class BaseClass {
    val typeMirror = runtimeMirror(this.getClass.getClassLoader)
    val instanceMirror = typeMirror.reflect(this)
    val members = instanceMirror.symbol.typeSignature.members
    def fieldMirror(symbol: Symbol) = instanceMirror.reflectField(symbol.asTerm)

    def fields = members.filter(_.typeSignature <:< typeOf[Field[_]])
    // filters all members that conform to type Field[_], i.e. also subclasses of Field
  }

  SomeClass.fields.foreach { symbol ⇒
    val name = symbol.name.toString.trim
    val required = symbol.typeSignature <:< typeOf[Required]
    val tpe = SomeClass.fieldMirror(symbol).get match {
      case field: Field[_] ⇒ field.tpe
    }
    println(s"field: $name; type=$tpe; required: $required")
    /** prints:
     *  field: requiredIntField; type=Int; required: true
     *  field: stringField; type=String; required: false
     */
  }
```

Credit goes to my colleague <a href="https://plus.google.com/100448717292118754458">Felix Geller</a> who discovered most of this first!

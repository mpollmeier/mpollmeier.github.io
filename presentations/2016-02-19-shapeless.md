## Practical(ly) shapeless
http://github.com/mpollmeier/shapeless-playground

Shapeless is a type level programming library for Scala. In this talk I will give a gentle introduction followed by some more advanced examples, e.g. a type level fold. Most importantly, the examples will not be of academic but of practical nature.
Live coding in shapeless-playground project.

# basics: HList
def list = List(5, "five") //problem: type Any
def tuple = (5, "five")    //problem: can't map over, limited to that one shape

import shapeless._
def hlist = 5 :: "five" :: HNil
* benefit: compile time knowledge about length and types
* downside: shapeless is complex and might scare your users - but we can do something about that

# you don't want users to call your function with an HList - just transform it from a tuple
import shapeless.syntax.std.product._
(5, "five").toHList

* to use in your function: define implicits (explicitly) - find them with your IDE
import shapeless.ops.product.ToHList
def tupleToHList[P <: Product, L <: HList](p: P)(
  implicit conv: ToHList.Aux[P,L]): L =
  p.toHList
def hlist = tupleToHList((5, "five"))

# the other end: if your function works with HLists internally, you don't want to return one - transform it TO a tuple
def tuple = hlist.tupled

* to use in your function: define used implicits (explicitly) - find them with your IDE
import shapeless.ops.hlist.Tupler
def convertToTuple[L <: HList, Out](l: L)(
  implicit tupler: Tupler.Aux[L, Out]
): Out = tupler(l)
def tuple = convertToTuple(5 :: "five" :: HNil)

# ensure at compile time that an HList (or tuple) has two or more elements
import shapeless.ops.hlist._

def atLeastTwo[L <: HList, H0, T0 <: HList](l: L)(
  implicit hasOne: IsHCons.Aux[L, H0, T0],
  hasTwo: IsHCons[T0])
  = "it worked"
def doesNotCompile = atLeastTwo(HNil)
def doesNotCompile = atLeastTwo(5 :: HNil)
def compiles = atLeastTwo(5 :: "five" :: HNil)

# polymorphic function values
import shapeless.poly._

object TypeMangler extends Poly1 {
  implicit def caseInt = at[Int](_ => "now it's a string")
  implicit def caseString = at[String](_.length)
}
def string = TypeMangler(5)
def int = TypeMangler("some string")
def hlist = 5 :: "five" :: HNil
def list = hlist map TypeMangler

# compose it all together: function that takes a tuple of at least two elements, uses a polymorphic map and returns a transformed tuple
import shapeless._
import shapeless.poly._
import shapeless.ops.hlist._
import shapeless.syntax.std.product._
import shapeless.ops.product.ToHList

object TypeMangler extends Poly1 {
  implicit def caseInt = at[Int](_ => "now it's a string")
  implicit def caseString = at[String](_.length)
}

def composeAll
  [TupleT <: Product,
  TupleAsHList <: HList,
  H0, T0 <: HList,
  MangledTupleAsHList <: HList,
  MangledTuple <: Product]
  (tuple: TupleT)
  (implicit toHList: ToHList.Aux[TupleT,TupleAsHList],
    hasOne: IsHCons.Aux[TupleAsHList, H0, T0],
    hasTwo: IsHCons[T0],
    typeMangler: Mapper.Aux[TypeMangler.type, TupleAsHList, MangledTupleAsHList],
    tupler: Tupler.Aux[MangledTupleAsHList, MangledTuple]
  ) = {
  val hlist: TupleAsHList = toHList(tuple)
  val mangledTupleAsHList = hlist map TypeMangler
  tupler(mangledTupleAsHList)
}

def doesNotCompile = composeAll(Tuple1(5))
def compiles = composeAll((5, "five"))

# Generic representation of case classes
case class Foo(i: Int, s: String, b: Boolean)
def fooGen = Generic[Foo]
def foo = Foo(23, "foo", true)
def hlist = fooGen.to(foo)

object TypeLevelProgramming {
  //type constructor, like a function at the type level. example for List[A]:
  type Id[A] = A
  //e.g. List[A]

  //higher kinded type (like higher order function)
  type Id2[A[_], B] = A[B]

  object Example {
    val someMap: Map[Option[Any], List[Any]] = Map(
      Some("foo") → List("a","b","c"),
      Some(42)    → List(1,2,3)
    )
    val x: List[String] = someMap(Some("foo")).asInstanceOf[List[String]]
  }

  object BetterWithHMap {

    val someMap = HMap[Option, List](
      Some("foo") → List("a","b","c"),
      Some(42)    → List(1,2,3)
    )
    val x: List[String] = someMap(Some("foo"))
    //val y: List[String] = someMap(Some(42)) //doesn't compile


    // magic start
    class HMap[K[_], V[_]](delegate: Map[K[Any], V[Any]]) {
      def apply[A](key: K[A]): V[A] =
        delegate(key.asInstanceOf[K[Any]]).asInstanceOf[V[A]]
    }

    object HMap {
      type Pair[K[_], V[_]] = (K[A], V[A]) forSome {type A}

      def apply[K[_], V[_]](tuples: (K[Any], V[Any])*) =
        new HMap[K,V](Map(tuples: _*))
    }
    // magic end
  }


  //Church encoding of booleans
  // put the code blocks into slides: http://apocalisp.wordpress.com/2010/06/13/type-level-programming-in-scala-part-3-boolean/
  object Booleans { 

    sealed trait Bool{
      type If[T,F]
    }
    sealed trait True extends Bool {
      type If[T,F] = T
    }
    sealed trait False extends Bool {
      type If[T,F] = F
    }

    //usages:
    type IntOrLong[A <: Bool] = A#If[Int,Long]
    implicitly[ IntOrLong[True] =:= Int ]
    //implicitly[ IntOrLong[True] =:= Long ] //doesn't compile: Cannot prove that Types.Booleans.IntOrLong[Types.Booleans.True] =:= Long.
    implicitly[ IntOrLong[False] =:= Long ]



    object PeanoNumbers {
      sealed trait Nat {
         type Match[NonZero, IfZero]
      }
      sealed trait _0 extends Nat {
         type Match[NonZero, IfZero] = IfZero
      }
      sealed trait Succ[N <: Nat] extends Nat {
         type Match[NonZero, IfZero] = NonZero
      }

      type _1 = Succ[_0]
      type _2 = Succ[_1]

      type ConstFalse = False
      type Is0[A <: Nat] = A#Match[ConstFalse, True]
      implicitly[Is0[_1] =:= False]
      //implicitly[Is0[_1] =:= True] //doesn't compile
    }
  }

  object ShapelessDemo {
    import shapeless._
    val a: Int::String::HNil = 42 :: "foo" :: HNil

  }

}

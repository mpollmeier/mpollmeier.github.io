---
layout: post
title: scala.util.Try - chain to handle all success and error cases
date: 2013-05-15 05:54:41.000000000 +02:00
permalink: scala-util-try-chain-to-handle-all-success-and-error-cases
tags: [scala]
---
scala.util.Try is pretty awesome and you should read Daniel's <a href="http://danielwestheide.com/blog/2012/12/26/the-neophytes-guide-to-scala-part-6-error-handling-with-try.html">introductory post</a> if you don't know it yet. I just ported some code from using exception handling to using Try: lines of code went down to a third, readability improved heavily.

If you need to handle all success and error cases you could chain Try ... recover, but that get's a bit clunky if you've got more than two Trys, because you have to indent it. Another option is to chain orElse:

```scala
def trySomething = Try { throw new Exception("didn't work"); -1 }
def trySomethingElse = Try { 42 }

trySomething orElse trySomethingElse map {
  case 42 ⇒ //wow, it worked
  case -1 ⇒ //no, this will never happen
} recover {
  case e: Throwable ⇒ // in case both failed
}
```

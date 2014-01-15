---
layout: post
title: ! 'Akka TestKit addition: expect some messages, ignore others'
date: 2013-03-26 05:28:05.000000000 +01:00
permalink: akka-testkit-expectmsgallofignoreothers
tags: [scala, akka, testing]
---
Here's an addition to Akka TestKit that might come in handy. It does what it says: you can give it a couple of messages that you're expecting within a given duration (defaults to 3s). Unlike expectMsgAllOf from the TestKit it ignores other messages that get sent to the testActor (expectMsgAllOf fails the test in that case). 

```scala
  import scala.collection.mutable
  def expectMsgAllOfIgnoreOthers[T](max: Duration, expected: T*) {
    val outstanding = mutable.Set(expected: _*)
    fishForMessage(max) {
      case msg: T if outstanding.contains(msg) ⇒
        outstanding.remove(msg)
        outstanding.isEmpty
      case _ ⇒ false
    }
  }

  def expectMsgAllOfIgnoreOthers[T](expected: T*) { 
    expectMsgAllOfIgnoreOthers(3 seconds, expected: _*) 
  }
```

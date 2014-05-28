---
layout: post
title: Callback lifter and exception driven development
date: 2013-05-11 22:51:25.000000000 +02:00
permalink: callback-lifter-and-exception-driven-development
tags: [scala, fun]
---
Warning: do not try this at work! ;)

We were hosting a <a href="http://www.meetup.com/kiwi-code-retreat/events/115115072/">code retreat</a> at work yesterday, and one of the sessions had the constraint that none of your production methods may return anything. I checked with our facilitator <a href="https://twitter.com/rapaul">Richard</a> and he said that callbacks are allowed. 
So <a href="https://twitter.com/talios">Mark</a> and I decided that each method that would normally return a value, would instead take call a callback function with that value. We would hand in that callback into the method. 
So we built a callback lifter that we would hand a function, which return value we're interested in. It calls the function and returns the value provided in the callback. With this in place we can use any function as if it would return a value ;)

Here it is. Quite concise actually...

```scala
def callbackLifter[T](func: ((T => Any)) => Unit): T = {
  var retVal = None: Option[T]
  func((x: T) => retVal = Some(x))
  retVal.get
}
```

And here's how to use is

```scala
def returnValueViaCallback(cb: (Int) => Any) { cb(3) }
callbackLifter(returnValueViaCallback) // 3
```

It also works with functions that take parameters

```scala
def withArgs(arg: Int, cb: (Int) => Any) { cb(arg) }
val partial = withArgs(99, _: (Int) => Any)
callbackLifter(partial) //99
```

We didn't actually get very far in the kata, but this was big fun!
Let me repeat my warning: this was just a fun exercise to see what's possible under funny constraints. Code retreats exist to drive some ideas to the extreme, which we did. ;)

In an adrenaline rush of good coding practices (I hope you read the sarcasm) we decided that mutable state in the callbackLifter (see the var?) is really bad. Exception driven development to the rescue! ;)

```scala
def exceptionDrivenCallbackLifter[T](func: ((T => Any)) => Unit): T =
  try {
    func((x: T) => throw new CallBackException[T](x))
    //the compiler forces us to return a T...
    null.asInstanceOf[T] 
  } catch {
    case CallBackException(x: T) => x
  }
```

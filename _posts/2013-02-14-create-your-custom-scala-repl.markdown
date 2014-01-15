---
layout: post
title: Create your custom Scala REPL
date: 2013-02-14 09:09:24.000000000 +01:00
permalink: create-your-custom-scala-repl
tags: [scala, repl]
---
If you ever need to create an interactive console/REPL that let's users use play with your code in a simple terminal you might (still) find that there's hardly any documentation. I fixed the console for <a href="https://github.com/mpollmeier/gremlin-scala">Gremlin-Scala</a> (a graph DSL) and thought that other people might want a very basic version of a custom shell to have a quick start. 
It doesn't do much more than importing java.lang.Math so that you can type things like `abs(-4.2)` directly in the shell without having to import `java.lang.Math._` first. The important part is that your import statements and any initialisation belongs into `addThunk` - those are executed after the shell is fully initialised - otherwise you get all sorts of AST errors that don't really help much. 

This is obviously only a starting point, you can further customise it to automatically do stuff with the executed command like we do in <a href="https://github.com/mpollmeier/gremlin-scala/blob/master/src/main/scala/com/tinkerpop/gremlin/scala/console/Console.scala">Gremlin-Scala's Console</a>.

```scala
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.ILoop

object TestConsole extends App {
  val settings = new Settings
  settings.usejavacp.value = true
  settings.deprecation.value = true

  new SampleILoop().process(settings)
}

class SampleILoop extends ILoop {
  override def prompt = "==> "

  addThunk {
    intp.beQuietDuring {
      intp.addImports("java.lang.Math._")
    }
  }

  override def printWelcome() {
    echo("\n" +
      "         \\,,,/\n" +
      "         (o o)\n" +
      "-----oOOo-(_)-oOOo-----")
  }

}
```

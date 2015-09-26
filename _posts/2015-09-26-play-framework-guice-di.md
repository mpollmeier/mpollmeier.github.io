---
layout: post
title: Overriding dependencies in a test with playframework and guice
date: 2015-09-25
permalink: 2015/09/25/playframework-guice-di
tags: [scala, recipe, test, playframework]
---

[Playframework](https://www.playframework.com/) recently made [guice](https://github.com/google/guice) its' default dependency injection (DI) framework. So I was looking for a simple example where a controller defines a dependency which then get's overridden for a FakeApplication in a test. I expected a simple gist in the docs for this very common (I thought) use case, but didn't find one. Here is what I ended up with, which is based on [ScalaTest + Play](http://www.scalatest.org/plus/play).

The nice thing about this setup is that it always tests the whole app, incl. the routing. Often developers only test a specific controller method, which leaves some space for bugs. 

app/controller/PersonController.scala:

```scala
class Hello {
  def sayHello(name: String) = "Hello " + name
}

class PersonController @Inject() (hello: Hello) extends Controller {
  def index = Action {
    Ok(hello.sayHello("michael"))
  }
}
```

test/ApplicationTest.scala:

```scala
import controllers._
import org.scalatest._
import org.scalatestplus.play._
import play.api.inject.bind
import play.api.inject.guice.GuiceInjector
import play.api.inject.guice.GuiceableModule
import play.api.test._
import play.api.test.Helpers._

abstract class MyPlaySpec extends WordSpec with Matchers with OptionValues with WsScalaTestClient

class GermanHello extends Hello {
  override def sayHello(name: String) = "Hallo " + name
}

class ApplicationTest extends MyPlaySpec with OneAppPerTestWithOverrides {
  override def overrideModules = Seq(
    bind[Hello].to[GermanHello]
  )

  "render the index page" in {
    val home = route(FakeRequest(GET, "/")).get
    status(home) shouldBe OK
    contentAsString(home) shouldBe "Hallo michael"
  }

}
```


test/OneAppPerTestWithOverrides.scala:

```scala
package org.scalatestplus.play

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test._
import org.scalatest._

trait OneAppPerTestWithOverrides extends SuiteMixin { this: Suite ⇒
  def overrideModules: Seq[GuiceableModule] = Nil

  def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
      .overrides(overrideModules: _*)
      .build

  private var appPerTest: Application = _
  implicit final def app: Application = synchronized { appPerTest }

  abstract override def withFixture(test: NoArgTest) = {
    synchronized { appPerTest = newAppForTest(test) }
    Helpers.running(app) {
      super.withFixture(test)
    }
  }
}
```

A full application (and test) is [here](https://github.com/mpollmeier/play-scala-di), and the discussion on the mailinglist [here](https://groups.google.com/forum/#!topic/play-framework/gW8FXC3K30E). More details on guice DI in playframework are in the [documentation](https://www.playframework.com/documentation/2.4.x/ScalaTestingWithGuice)

PS: another handy tipp: if you want to get the used binding for a given class, you can just ask the guice injector inside a test:

```scala
val hello = app.injector.instanceOf(classOf[Hello])
```

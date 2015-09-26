---
layout: post
title: Http Basic Auth as a composable Action in Playframework
date: 2015-09-26
permalink: 2015/09/26/playframework-basic-auth
tags: [scala, recipe, test, playframework, security]
---

This is a quick recipe for securing a [playframework](https://www.playframework.com/) controller with http basic auth. It parses the basic auth header and validates it against a set of valid users. It's just hard coded in this example - just plug in your own user management.

ActionFilters.scala:

```scala
import AuthenticationHelpers._
import java.util.Base64
import play.api.mvc._
import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedBuilder

object Authenticated extends AuthenticatedBuilder(
  _.headers.get("Authorization")
    .flatMap(parseAuthHeader)
    .flatMap(validateUser),
  onUnauthorized = { _ ⇒
    Unauthorized(views.html.defaultpages.unauthorized())
      .withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")
  }
)

object AuthenticationHelpers {
  val validCredentials = Set(
    Credentials(User("michael"), Password("correct password"))
  )

  def authHeaderValue(credentials: Credentials) =
    "Basic " + Base64.getEncoder.encodeToString(s"${credentials.user.value}:${credentials.password.value}".getBytes)

  def parseAuthHeader(authHeader: String): Option[Credentials] =
    authHeader.split("""\s""") match {
      case Array("Basic", userAndPass) ⇒
        new String(Base64.getDecoder.decode(userAndPass), "UTF-8").split(":") match {
          case Array(user, password) ⇒ Some(Credentials(User(user), Password(password)))
          case _                     ⇒ None
        }
      case _ ⇒ None
    }

  def validateUser(c: Credentials): Option[User] =
    if (validCredentials.contains(c))
      Some(c.user)
    else
      None
}

case class Credentials(user: User, password: Password)
case class User(value: String) extends AnyVal
case class Password(value: String) extends AnyVal
```

Usage in your controller:

```scala
def index = Authenticated { req ⇒
  Ok()
}
```

And in your test:

```scala
val requestValidAuth = FakeRequest().withHeaders(
("Authorization", AuthenticationHelpers.authHeaderValue(
  Credentials(User("michael"), Password("correct password")))
))
val response = controller.someEndpoint()(requestValidAuth)
status(response) shouldBe OK
```

If a user hits that endpoint, her browser will pop up a username/password dialog.
The source for a full application is available [here](https://github.com/mpollmeier/play-simple).

package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.smqd.util.FailFastCirceSupport._
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import io.circe.Json

class AuthController(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives  {
  override def routes: Route = securityCheck

  private def securityCheck: Route = {
    ignoreTrailingSlash {
      parameters( 'j_username, 'j_password, 'forceDefaultRedirect.as[Boolean].?, 'userLocale.?, 'userTimezone.?) {
        case (username, password, forceDefaultRedirect, userLocale, userTimezone) =>
          complete(StatusCodes.OK, Json.obj(("success", Json.fromBoolean(true))))
        case _ =>
          complete(StatusCodes.BadRequest)
      } ~
      parameters('ticket) { ticket =>
        complete(StatusCodes.InternalServerError)
      }
    }
  }

}

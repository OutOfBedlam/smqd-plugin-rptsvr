// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

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

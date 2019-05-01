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

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import com.thing2x.rptsvr.api.UserHandler.{Role, User}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

object UserHandler {
  case class Role(name: String,
                  externallyDefined: Boolean)

  case class User(username: String,
                  fullName: String,
                  previousPasswordChangeTime: String,
                  tenantId: String,
                  roles: Seq[Role],
                  externallyDefined: Boolean,
                  enabled: Boolean
                 )
}

class UserHandler()(implicit executionContex: ExecutionContext) {

  def getUser(username: String, organization: Option[String]): Future[(StatusCode, Json)] = Future {
    val tenantId = organization.getOrElse("")
    val user = User(username = username,
      fullName="Joe User",
      previousPasswordChangeTime = "2019-04-24T01:15:14.000+0000",
      tenantId = tenantId,
      roles = Seq(Role("ROLE_USER", externallyDefined = false)),
      externallyDefined = false,
      enabled = true
    )
    (StatusCodes.OK, user.asJson)
  }
}

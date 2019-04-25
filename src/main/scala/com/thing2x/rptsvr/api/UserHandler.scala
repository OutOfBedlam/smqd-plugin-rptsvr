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

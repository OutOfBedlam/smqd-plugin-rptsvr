package com.thing2x.rptsvr

import com.thing2x.rptsvr.UserHandler.{Role, User}

import scala.concurrent.{ExecutionContext, Future}

object UserHandler {
  case class Role(name: String,
                  externallyDefined: Boolean)

  case class User(username: String,
                  fullName: String,
                  previousPasswordChangeTime: String,
                  roles: Seq[Role],
                  externallyDefined: Boolean,
                  enabled: Boolean
                 )
}

class UserHandler()(implicit executionContex: ExecutionContext) {

  def getUser(username: String): Future[User] = Future {
    User(username = username,
      fullName="Joe User",
      previousPasswordChangeTime = "2019-04-19T18:53:07.602-07:00",
      roles = Seq(Role("ROLE_USER", externallyDefined = false)),
      externallyDefined = false,
      enabled = true
    )
  }
}

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

package com.thing2x.rptsvr

import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import io.circe.{ACursor, DecodingFailure, Json}

import scala.collection.mutable

object JdbcDataSourceResource {
  def apply(uri: String, label: String)(implicit context: RepositoryContext) = new JdbcDataSourceResource(uri, label)
}

class JdbcDataSourceResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends DataSourceResource {
  override val resourceType: String = "jdbcDataSource"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("repository.jdbcDataSource+json", HttpCharsets.`UTF-8`)

  var driverClass: Option[String] = None
  var username: Option[String] = None
  var password: Option[String] = None
  var connectionUrl: Option[String] = None
  var timezone: Option[String] = None

  override def encodeFields(expanded: Boolean): Map[String, Json] = {
    val map: mutable.Map[String, Json] = mutable.Map.empty
    if (driverClass.isDefined) map("driverClass") = Json.fromString(driverClass.get)
    if (password.isDefined) map("password") = Json.fromString(password.get)
    if (username.isDefined) map("username") = Json.fromString(username.get)
    if (connectionUrl.isDefined) map("connectionUrl") = Json.fromString(connectionUrl.get)
    if (timezone.isDefined) map("timezone") = Json.fromString(timezone.get)
    Map(map.toSeq:_*)
  }

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    driverClass = cur.downField("driverClass").as[Option[String]].right.get
    username = cur.downField("username").as[Option[String]].right.get
    password = cur.downField("password").as[Option[String]].right.get
    connectionUrl = cur.downField("connectionUrl").as[Option[String]].right.get
    timezone = cur.downField("timezone").as[Option[String]].right.get
    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }

  override def toString: String = {
    s"JdbcDataSourceResource(uri=$uri, label=$label, resourceType=$resourceType, driverClass=$driverClass, connectionUrl=$connectionUrl)"
  }

}

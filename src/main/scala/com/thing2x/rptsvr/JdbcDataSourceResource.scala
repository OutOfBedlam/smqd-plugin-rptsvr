package com.thing2x.rptsvr

import io.circe.{ACursor, DecodingFailure, Json}

import scala.collection.mutable

class JdbcDataSourceResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "jdbcDataSource"

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
}

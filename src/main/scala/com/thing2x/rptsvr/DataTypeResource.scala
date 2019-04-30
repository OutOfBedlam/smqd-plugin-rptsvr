package com.thing2x.rptsvr
import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import io.circe.{ACursor, DecodingFailure, Json}

import scala.collection.mutable

class DataTypeResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "dataType"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("repository.dataType+json", HttpCharsets.`UTF-8`)

  var `type`: String = "text" // "text|number|date|dateTime|time"
  var pattern: Option[String] = None
  var maxValue: Option[String] = None
  var minValue: Option[String] = None
  var maxLength: Option[Int] = None
  var strictMax: Boolean = false
  var strictMin: Boolean = false

  override def encodeFields(expanded: Boolean): Map[String, Json] = {
    val map: mutable.Map[String, Json] = mutable.Map.empty
    map("type") = Json.fromString(`type`)
    map("strictMax") = Json.fromBoolean(strictMax)
    map("strictMin") = Json.fromBoolean(strictMin)
    if (maxValue.isDefined) map("maxValue") = Json.fromString(maxValue.get)
    if (maxLength.isDefined) map("maxLength") = Json.fromInt(maxLength.get)
    if (minValue.isDefined) map("minValue") = Json.fromString(minValue.get)
    if (pattern.isDefined) map("pattern") = Json.fromString(pattern.get)
    Map(map.toSeq:_*)
  }

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    `type` = cur.downField("type").as[String].right.get
    pattern = cur.downField("pattern").as[Option[String]].right.get
    maxValue = cur.downField("maxValue").as[Option[String]].right.get
    minValue = cur.downField("minValue").as[Option[String]].right.get
    maxLength = cur.downField("maxLength").as[Option[Int]].right.get
    strictMax = cur.downField("strictMax").as[Boolean].right.getOrElse(false)
    strictMin = cur.downField("strictMin").as[Boolean].right.getOrElse(false)
    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }
}

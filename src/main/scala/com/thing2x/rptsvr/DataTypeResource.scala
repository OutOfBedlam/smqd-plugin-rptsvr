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

class DataTypeResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "dataType"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("repository.dataType+json", HttpCharsets.`UTF-8`)

  var `type`: String = "text" // "text|number|date|dateTime|time"
  var pattern: Option[String] = None
  var maxValue: Option[String] = None // base64 encoded binary data
  var minValue: Option[String] = None // base64 encoded binary data
  var maxLength: Option[Int] = None
  var decimals: Option[Int] = None
  var regularExpr: Option[String] = None
  var strictMax: Boolean = false
  var strictMin: Boolean = false

  def typeId: Int = {
    `type` match {
      case "text" => 1
      case "number" => 2
      case "date" => 3
      case "dateTime" => 4
      case "time" => 5
    }
  }

  override def encodeFields(expanded: Boolean): Map[String, Json] = {
    val map: mutable.Map[String, Json] = mutable.Map.empty
    map("type") = Json.fromString(`type`)
    map("strictMax") = Json.fromBoolean(strictMax)
    map("strictMin") = Json.fromBoolean(strictMin)
    if (maxValue.isDefined) map("maxValue") = Json.fromString(maxValue.get)
    if (maxLength.isDefined) map("maxLength") = Json.fromInt(maxLength.get)
    if (minValue.isDefined) map("minValue") = Json.fromString(minValue.get)
    if (pattern.isDefined) map("pattern") = Json.fromString(pattern.get)
    if (decimals.isDefined) map("decimals") = Json.fromInt(decimals.get)
    if (regularExpr.isDefined) map("regularExpr") = Json.fromString(regularExpr.get)
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
    decimals = cur.downField("decimals").as[Option[Int]].right.get
    regularExpr = cur.downField("regularExpr").as[Option[String]].right.get
    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }
}

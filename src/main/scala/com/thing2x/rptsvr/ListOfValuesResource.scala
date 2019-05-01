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

class ListOfValuesResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "listOfValues"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("repository.listOfValues+json", HttpCharsets.`UTF-8`)

  var items: Map[String, String] = Map.empty

  override def encodeFields(expanded: Boolean): Map[String, Json] = {
    Map(
      "items" -> Json.arr(
        items.map { case(name, value) =>
          Json.obj("label" -> Json.fromString(name),
            "value" -> Json.fromString(value))
        }.toSeq: _*
      )
    )
  }

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    var arrCur = cur.downField("items").downArray
    val map: mutable.Map[String, String] = mutable.Map.empty
    while(arrCur.succeeded) {
      val k = arrCur.downField("label").as[String].right.get
      val v = arrCur.downField("value").as[String].right.get
      map(k) = v
      arrCur = arrCur.right
    }
    items = Map(map.toSeq:_*)
    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }
}

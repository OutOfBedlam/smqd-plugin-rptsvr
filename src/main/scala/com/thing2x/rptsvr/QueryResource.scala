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

class QueryResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "query"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("application/repository.query+json", HttpCharsets.`UTF-8`)

  var query: String = ""
  var language: String = ""
  var dataSource: Option[DataSourceResource] = None

  override def encodeFields(expanded: Boolean): Map[String, Json] = {
    val map: mutable.Map[String, Json] = mutable.Map.empty
    map("value") = Json.fromString(query)
    map("language") = Json.fromString(language)
    if (dataSource.isDefined) map("dataSource") = dataSource.get.asJson(expanded)
    Map(map.toSeq:_*)
  }

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    query = cur.downField("value").as[String].right.get
    language = cur.downField("language").as[String].right.get

    decodeReferencedResource[DataSourceResource](cur.downField("dataSource"), "dataSource", "dataSource") match {
      case Right(r) => dataSource = Some(r)
      case _ => dataSource = None
    }

    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }
}

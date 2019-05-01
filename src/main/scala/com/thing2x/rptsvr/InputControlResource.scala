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

///////////////////////////////////////////////////////////////////////////////
// type   Type of Input Control                         usedFields
// ----------------------------------------------------------------------------
//  1            Boolean                                None
//  2         Single value                              dataType
//  3      Single-select list of values                 listOfValues
//  4          Single-select query                      query; queryValueColumn
//  5            Not used
//  6      Multi-select list of values                  listOfValues
//  7        Multi-select query                         query; queryValueColumn
//  8    Single-select list of values radio buttons     listOfValues
//  9      Single-select query radio buttons            query; queryValueColumn
//  10    Multi-select list of values check boxes       listOfValues
//  11    Multi-select query check boxes                query; queryValueColumn
///////////////////////////////////////////////////////////////////////////////
class InputControlResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource  {
  override val resourceType: String = "inputControl"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("repository.inputControl+json", HttpCharsets.`UTF-8`)

  var mandatory: Boolean = false
  var readOnly: Boolean = false
  var visible: Boolean = true
  var controlType: Int = 5  // inputControlTypeByteValue}
  var usedFields: Seq[String] = Seq.empty
  var visibleColumns: Seq[String] = Seq.empty

  var dataType: Option[DataTypeResource] = None

  var listOfValues: Option[ListOfValuesResource] = None

  var query: Option[QueryResource] = None
  var valueColumn: Option[String] = None

  override def encodeFields(expanded: Boolean): Map[String, Json] = {
    val map: mutable.Map[String, Json] = mutable.Map.empty
    map("mandatory") = Json.fromBoolean(mandatory)
    map("readOnly") = Json.fromBoolean(readOnly)
    map("visible") = Json.fromBoolean(visible)
    map("type") = Json.fromInt(controlType)
    if (usedFields.nonEmpty) map("usedFields") = Json.fromString(usedFields.mkString("; "))
    if (visibleColumns.nonEmpty) map("visibleColumns") = Json.arr(visibleColumns.map(Json.fromString):_*)

    if (dataType.isDefined) {
      map("dataType") = if (expanded) {
        Json.obj("dataType" -> dataType.get.asJson(expanded))
      }
      else {
        Json.obj("dataTypeReference" -> Json.obj("uri" -> Json.fromString(dataType.get.uri)))
      }
    }

    if (listOfValues.isDefined) {
      map("listOfValues") = if (expanded) {
        Json.obj("listOfValues" -> listOfValues.get.asJson(expanded))
      }
      else {
        Json.obj("listOfValuesReference" -> Json.obj("uri" -> Json.fromString(listOfValues.get.uri)))
      }
    }

    if (query.isDefined){
      map("query") = if (expanded) {
        Json.obj("query" -> query.get.asJson(expanded))
      }
      else {
        Json.obj("queryReference" -> Json.obj( "uri" -> Json.fromString(query.get.uri)))
      }
    }
    if (valueColumn.isDefined) map("valueColumn") = Json.fromString(valueColumn.get)

    Map(map.toSeq:_*)
  }

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    mandatory   = cur.downField("mandatory").as[Boolean].right.get
    readOnly    = cur.downField("readOnly").as[Boolean].right.get
    visible     = cur.downField("visible").as[Boolean].right.get
    controlType = cur.downField("type").as[Int].right.get
    val fields  = cur.downField("usedFields").as[Option[String]].right.get
    usedFields = fields match {
      case Some(str) =>
        str.split(';').map(_.trim).toSeq
      case _ => Seq.empty
    }
    visibleColumns = cur.downField("visibleColumns").downArray.as[Option[Seq[String]]].right.get.getOrElse(Seq.empty)

    decodeReferencedResource[DataTypeResource](cur.downField("dataType"), "dataType", "dataType") match {
      case Right(r) => dataType = Some(r)
      case _ => dataType = None
    }
    decodeReferencedResource[ListOfValuesResource](cur.downField("listOfValues"), "listOfValues", "listOfValues") match {
      case Right(r) => listOfValues = Some(r)
      case _ => listOfValues = None
    }
    decodeReferencedResource[QueryResource](cur.downField("query"), "query", "query") match {
      case Right(r) => query = Some(r)
      case _ => query = None
    }

    valueColumn = cur.downField("valueColumn").as[Option[String]].right.get

    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
    if (dataType.isDefined)
      context.repository.setResource(dataType.get.uri, dataType.get, createFolders = true, overwrite = true)

    if (listOfValues.isDefined)
      context.repository.setResource(listOfValues.get.uri, listOfValues.get, createFolders = true, overwrite = true)

    if (query.isDefined)
      context.repository.setResource(query.get.uri, query.get, createFolders = true, overwrite = true)
  }
}

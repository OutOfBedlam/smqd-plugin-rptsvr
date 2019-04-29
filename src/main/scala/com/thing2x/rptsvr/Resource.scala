package com.thing2x.rptsvr

import java.io.File

import akka.http.scaladsl.model.ContentType
import io.circe.Json
import io.circe.syntax._

sealed trait Resource {
  val uri: String
  val label: String
  val permissionMask: Int
  val description: Option[String]
  val version: Int
  val creationDate: String
  val updateDate: String

  val resourceType: String

  def asJson: Json = asJson(true)

  def asJson(expanded: Boolean): Json = {
    val properties = Map("uri" -> Json.fromString(uri),
      "label" -> Json.fromString(label),
      "permissionMask" -> Json.fromInt(permissionMask),
      "creationDate" -> Json.fromString(creationDate),
      "updateDate" -> Json.fromString(updateDate),
      "description" -> Json.fromString(description.getOrElse(""))) ++ resourceSpecificFields(expanded)
    Json.obj(properties.toSeq:_*)
  }

  def asLookupResult: Json = {
    val properties = Map("uri" -> Json.fromString(uri),
      "label" -> Json.fromString(label),
      "permissionMask" -> Json.fromInt(permissionMask),
      "creationDate" -> Json.fromString(creationDate),
      "updateDate" -> Json.fromString(updateDate),
      "description" -> Json.fromString(description.getOrElse("")),
      "resourceType" -> Json.fromString(resourceType),
    )
    Json.obj(properties.toSeq:_*)
  }

  def resourceSpecificFields(expanded: Boolean): Map[String, Json] = Map.empty
}

class FolderResource(val uri: String, val label: String, val permissionMask: Int, val description: Option[String], val version: Int, val creationDate: String, val updateDate: String) extends Resource {
  override val resourceType: String = "folder"
}

class FileResource(val uri: String, val label: String, val permissionMask: Int, val description: Option[String], val version: Int, val creationDate: String, val updateDate: String, val `type`: String) extends Resource {

  override def resourceSpecificFields(expanded: Boolean): Map[String, Json] =
    Map("type"->Json.fromString(`type`))

  override val resourceType: String = "file"
}

case class FileContent(uri: String, file: File, contentType: ContentType)

//case class JndiDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                                  jndiName: String, timezone: String) extends Resource
//
//case class JdbcDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                                  driverClass: String, username: String, password: String, connectionUrl: String, timezone: String) extends Resource
//
//case class AwsDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                                 driverClass: String, username: String, password: String, connectionUrl: String, timezone: String,
//                                 accessKey: String, secretKey: String, roleArn: String, region: String, dbName: String, dbInstanceIdentifier: String, dbService: String) extends Resource
//
//case class SubDataSource(id: String, uri: String)
//
//case class VirtualDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                                     subDataSources: Seq[SubDataSource]) extends Resource
//
//case class BeanDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                                  beanName: String, beanMethod: String) extends Resource
//
//case class DatatypesResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                             pattern: String, maxValue: String, strictMax: Boolean, minValue: String, strictMin: Boolean, maxLength: Int) extends Resource
//
//case class ReferenceUri(uri: String)
//
//case class DataSourceReference(dataSourceReference: ReferenceUri)
//
//case class QueryResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
//                         value: String, language: String, dataSource: DataSourceReference) extends Resource

class ReportUnitResource(val uri: String, val label: String, val permissionMask: Int, val description: Option[String], val version: Int, val creationDate: String, val updateDate: String) extends Resource {
  var alwaysPromptControls: Boolean = true
  var controlsLayout: String = "popupScreen"
  var jrxml: Option[FileResource] = None
  var resources: Map[String, FileResource] = Map.empty

  override val resourceType: String = "reportUnit"

  override def resourceSpecificFields(expanded: Boolean): Map[String, Json] = Map(
    "alwaysPromptControls" -> Json.fromBoolean(alwaysPromptControls),
    "controlsLayout" -> Json.fromString(controlsLayout),
    "jrxml" -> (
      if (jrxml.isDefined) {
        if (expanded) {
          Json.obj("jrxmlFile" -> jrxml.get.asJson)
        }
        else {
          Json.obj("jrxmlFileReference" -> Json.obj(
            "uri" -> Json.fromString(jrxml.get.uri)
          ))
        }
      }
      else {
        Json.Null
      }
      ),
    "resources" -> Json.obj(
      "resource" ->
        resources.map { case (name, r) =>
          Json.obj("name" -> Json.fromString(name), "file" -> (
            if (expanded)
              Json.obj( "fileResource" -> r.asJson)
            else
              Json.obj("fileReference" -> Json.obj("uri" -> Json.fromString(r.uri)))
            ))
        }.asJson
    )
  )
}

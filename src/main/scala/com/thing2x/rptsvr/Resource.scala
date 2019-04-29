package com.thing2x.rptsvr

import java.io.File
import java.util.Date

import akka.http.scaladsl.model.ContentType
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import io.circe.{ACursor, DecodingFailure, HCursor, Json}

object Resource extends StrictLogging {

  def apply(json: Json)(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = {
    apply(json.hcursor)
  }

  def apply(cur: ACursor)(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = {
    try {
      val resourceType = cur.downField("resourceType").as[String] match {
        case Right(v) => v.toLowerCase()
        case _ => "file"
      }
      val uri = cur.downField("uri").as[String].right.get
      val label = cur.downField("label").as[String].right.get
      val ver = cur.downField("version").as[Option[Int]].right.get
      val permissionMask = cur.downField("version").as[Option[Int]].right.get
      val creationTime = cur.downField("creationTime").as[Option[Long]].right.get
      val updateTime = cur.downField("updateTime").as[Option[Long]].right.get
      val description = cur.downField("description").as[Option[String]].right.get

      logger.debug(s"decode json: $resourceType, $uri, $label, $ver, $description")
      val rt = resourceType.toLowerCase match {
        case "folder" =>     new FolderResource(uri, label)
        case "file" =>       new FileResource(uri, label)
        case "reportunit" => new ReportUnitResource(uri, label)
      }
      rt.permissionMask = permissionMask.getOrElse(0)
      rt.version = ver.getOrElse(-1)
      rt.description = description

      if (creationTime.isDefined)
        rt.creationDate = new Date(creationTime.get)

      if (updateTime.isDefined)
        rt.updateDate = new Date(updateTime.get)

      rt.decodeFields(cur)

    } catch {
      case ex: Throwable =>
        logger.error("resource parse error", ex)
        Left(DecodingFailure(ex.getMessage, cur.history))
    }
  }
}

abstract class Resource {
  //////////////////////////////
  // common attributes
  val uri: String
  val label: String
  var permissionMask: Int = 0
  var version: Int = -1

  var description: Option[String] = None
  def description_=(desc: String): Unit = description = Some(desc)

  var creationDate: Option[Date] = None
  var updateDate: Option[Date] = None

  def creationDate_=(d: Date): Unit = creationDate = Some(d)
  def updateDate_=(d: Date): Unit = updateDate = Some(d)

  //////////////////////////////
  // lookup resources
  val resourceType: String

  //////////////////////////////
  // asJson
  def asJson(implicit context: RepositoryContext): Json = asJson( expanded = true )

  def asJson(expanded: Boolean)(implicit context: RepositoryContext): Json = {
    var attr = Map(
      "uri" -> Json.fromString(uri),
      "label" -> Json.fromString(label),
      "version" -> Json.fromInt(version),
      "permissionMask" -> Json.fromInt(permissionMask))

    if (description.isDefined)
      attr ++= Map("description" -> Json.fromString(description.get))

    if (creationDate.isDefined)
      attr ++= Map("creationDate" -> Json.fromString(context.datetimeFormat.format(creationDate.get)))

    if (updateDate.isDefined)
      attr ++= Map("updateDate" -> Json.fromString(context.datetimeFormat.format(updateDate.get)))

    attr ++= encodeFields(expanded)
    Json.obj(attr.toSeq:_*)
  }

  def asLookupResult(implicit context: RepositoryContext): Json = {
    var attr = Map(
      "uri" -> Json.fromString(uri),
      "label" -> Json.fromString(label),
      "version" -> Json.fromInt(version),
      "permissionMask" -> Json.fromInt(permissionMask),
      "resourceType" -> Json.fromString(resourceType))

    if (description.isDefined)
      attr ++= Map("description" -> Json.fromString(description.get))

    if (creationDate.isDefined)
      attr ++= Map("creationDate" -> Json.fromString(context.datetimeFormat.format(creationDate.get)))

    if (updateDate.isDefined)
      attr ++= Map("updateDate" -> Json.fromString(context.datetimeFormat.format(updateDate.get)))

    Json.obj(attr.toSeq:_*)
  }

  //////////////////////////////
  // encode/deocode resource specific fields
  def encodeFields(expanded: Boolean)(implicit context: RepositoryContext): Map[String, Json]
  def decodeFields(cur: ACursor)(implicit context: RepositoryContext): Either[DecodingFailure, Resource]
}

class FolderResource(val uri: String, val label: String) extends Resource {
  override val resourceType: String = "folder"
  override def encodeFields(expanded: Boolean)(implicit context: RepositoryContext): Map[String, Json] = Map.empty
  override def decodeFields(cur: ACursor)(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = Right(this)
}

class FileResource(val uri: String, val label: String) extends Resource {
  var fileType: String = ""

  override val resourceType: String = "file"

  override def encodeFields(expanded: Boolean)(implicit context: RepositoryContext): Map[String, Json] =
    Map("type"->Json.fromString(fileType))

  override def decodeFields(cur: ACursor)(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = {
    fileType = cur.downField("type").as[String].right.get
    Right(this)
  }
}

case class FileContent(uri: String, file: File, contentType: ContentType)

class ReportUnitResource(val uri: String, val label: String) extends Resource {
  var alwaysPromptControls: Boolean = true
  var controlsLayout: String = "popupScreen"
  var jrxml: Option[FileResource] = None
  var resources: Map[String, FileResource] = Map.empty

  override val resourceType: String = "reportUnit"

  override def encodeFields(expanded: Boolean)(implicit context: RepositoryContext): Map[String, Json] = Map(
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

  override def decodeFields(cur: ACursor)(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = {
    alwaysPromptControls = cur.downField("alwaysPromptControls").as[Boolean].right.get
    controlsLayout = cur.downField("controlsLayout").as[String].right.get
    val jcur = cur.downField("jrxml")
    val jrxmlRefCur = jcur.downField("jrxmlFileReference")
    val jrxmlFileCur = jcur.downField("jrxmlFile")

    if (jrxmlRefCur.succeeded) {
      println("========+> Reference..............................")
    }
    else if (jrxmlFileCur.succeeded) {
      Resource(jrxmlFileCur) match {
        case Right(r) if r.isInstanceOf[FileResource] => jrxml = Some(r.asInstanceOf[FileResource])
        case _ => jrxml = None
      }
      println(s"========+> File................................: $jrxml")
    }

    var resourceCur = cur.downField("resources").downField("resource").downArray
    var resourceMap: Map[String, FileResource] = Map.empty

    while( resourceCur.succeeded ) {
      val name = resourceCur.downField("name").as[String].right.get
      val filePos = resourceCur.downField("file")
      val fileRefCur = filePos.downField("fileReference")
      val fileRscCur = filePos.downField("fileResource")
      if (fileRefCur.succeeded) {
        println(s"===================> Resource $name --> ${fileRefCur.downField("uri")}")
        None
      }
      else if (fileRscCur.succeeded) {
        println(s"===================> Resource $name --> ${fileRscCur.downField("uri")}")
        Resource(fileRscCur) match {
          case Right(r) if r.isInstanceOf[FileResource] => resourceMap ++= Map(name -> r.asInstanceOf[FileResource])
          case _ => None
        }
      }
      else {
        None
      }

      resourceCur = resourceCur.right
    }
    resources = resourceMap

    Right(this)
  }

}

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

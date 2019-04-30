package com.thing2x.rptsvr

import java.util.Date

import akka.http.scaladsl.model.MediaType
import com.typesafe.scalalogging.StrictLogging
import io.circe.{ACursor, CursorOp, DecodingFailure, Json}

import scala.concurrent.{Await, ExecutionContext}

object Resource extends StrictLogging {

  def apply(json: Json, defaultResourceType: String = "file")(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = {
    apply(json.hcursor, defaultResourceType)
  }

  def apply(cur: ACursor, defaultResourceType: String)(implicit context: RepositoryContext): Either[DecodingFailure, Resource] = {
    try {
      val resourceType = cur.downField("resourceType").as[String] match {
        case Right(v) => v.toLowerCase()
        case _ => defaultResourceType
      }
      val uri = cur.downField("uri").as[String].right.get
      val label = cur.downField("label").as[String].right.get
      val ver = cur.downField("version").as[Option[Int]].right.get
      val permissionMask = cur.downField("permissionMask").as[Option[Int]].right.get
      val creationTime = cur.downField("creationTime").as[Option[Long]].right.get
      val updateTime = cur.downField("updateTime").as[Option[Long]].right.get
      val description = cur.downField("description").as[Option[String]].right.get

      logger.trace(s"decode json uri=$uri resourceType=$resourceType label=$label ver=$ver desc=$description")
      val rt = resourceType.toLowerCase match {
        case "folder"         => new FolderResource(uri, label)
        case "file"           => new FileResource(uri, label)
        case "reportunit"     => new ReportUnitResource(uri, label)
        case "jdbcdatasource" => new JdbcDataSourceResource(uri, label)
        case "datatype"       => new DataTypeResource(uri, label)
        case "inputcontrol"   => new InputControlResource(uri, label)
        case "listofvalues"   => new ListOfValuesResource(uri, label)
        case "query"          => new QueryResource(uri, label)
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

abstract class Resource(implicit context: RepositoryContext) extends StrictLogging {
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

  // media type that sent to the client
  val mediaType: MediaType.WithFixedCharset

  // write only field
  var content: Option[String] = None

  //////////////////////////////
  // asJson
  def asJson: Json = asJson( expanded = true )

  def asJson(expanded: Boolean): Json = asJson(expanded, asLookupResult = false)

  def asLookupResult: Json = asJson(expanded = false, asLookupResult = true)

  private def asJson(expanded: Boolean, asLookupResult: Boolean): Json = {
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

    if (asLookupResult) {
      attr ++= Map("resourceType" -> Json.fromString(resourceType))
    }

    if (!asLookupResult) {
      attr ++= encodeFields(expanded)
    }
    Json.obj(attr.toSeq:_*)
  }

  def asMeta: Json = {
    var attr = Map(
      "uri" -> Json.fromString(uri),
      "label" -> Json.fromString(label),
      "version" -> Json.fromInt(version),
      "permissionMask" -> Json.fromInt(permissionMask))

    if (description.isDefined)  attr ++= Map("description" -> Json.fromString(description.get))
    if (creationDate.isDefined) attr ++= Map("creationTime" -> Json.fromLong(creationDate.get.getTime))
    if (updateDate.isDefined)   attr ++= Map("updateTime" -> Json.fromLong(updateDate.get.getTime))

    attr ++= Map("resourceType" -> Json.fromString(resourceType))
    attr ++= encodeFields( expanded = false)
    Json.obj(attr.toSeq:_*)
  }

  //////////////////////////////
  // encode/deocode resource specific fields
  def encodeFields(expanded: Boolean): Map[String, Json]
  def decodeFields(cur: ACursor): Either[DecodingFailure, Resource]

  //////////////////////////////
  // write resource to repository
  def write(writer: ResourceWriter): Unit

  //////////////////////////////
  // loading child resource

  // reference field name = 'fieldName'+"Reference"
  //     jrxmlFile
  //     jrxmlFileReference
  def decodeReferencedResource[T <: Resource](cur: ACursor, fieldName: String, fieldType: String): Either[DecodingFailure, T] = {
    decodeReferencedResource(cur, fieldName, s"${fieldName}Reference", fieldType)
  }

  // reference field name is not 'fieldName'+"Reference"
  //     fileResource
  //     fileReference
  def decodeReferencedResource[T <: Resource](cur: ACursor, resourceFieldName: String, referenceFieldName: String, fieldType: String): Either[DecodingFailure, T] = {
    val refCur = cur.downField(referenceFieldName)
    val valCur = cur.downField(resourceFieldName)
    if (refCur.succeeded) {
      implicit val ec: ExecutionContext = context.executionContext
      import scala.concurrent.duration._
      val path = refCur.downField("uri").as[String].right.get
      val future = context.repository.getResource(path)
      Await.result(future, 5.seconds) match {
        case Right(r) => Right(r.asInstanceOf[T])
        case _ =>  Left(DecodingFailure(s"Referenced resource: $fieldType failed to load from $path", Nil))
      }
    }
    else if (valCur.succeeded) {
      Resource(valCur, fieldType) match {
        case Right(r) => Right(r.asInstanceOf[T])
        case _ => Left(DecodingFailure(s"Referenced resource: $fieldType failed to decode as $fieldType", Nil))
      }
    }
    else {
      Left(DecodingFailure(s"Referenced resource: $fieldType failed to load", Nil))
    }
  }

}


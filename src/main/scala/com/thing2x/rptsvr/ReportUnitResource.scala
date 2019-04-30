package com.thing2x.rptsvr

import io.circe.syntax._
import io.circe.{ACursor, DecodingFailure, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class ReportUnitResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  var alwaysPromptControls: Boolean = true
  var controlsLayout: String = "popupScreen"
  var jrxml: Option[FileResource] = None
  var resources: Map[String, FileResource] = Map.empty

  override val resourceType: String = "reportUnit"

  override def encodeFields(expanded: Boolean): Map[String, Json] = Map(
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

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    alwaysPromptControls = cur.downField("alwaysPromptControls").as[Boolean].right.get
    controlsLayout = cur.downField("controlsLayout").as[String].right.get
    val jcur = cur.downField("jrxml")
    val jrxmlRefCur = jcur.downField("jrxmlFileReference")
    val jrxmlFileCur = jcur.downField("jrxmlFile")

    if (jrxmlRefCur.succeeded) {
      val ref = jrxmlRefCur.downField("uri").as[String]
      if (ref.isRight){
        val path = ref.right.get
        implicit val ec: ExecutionContext = context.executionContext
        val future = context.repository.getResource(path)
        Await.result(future, 5.seconds) match {
          case Right(r) =>
            jrxml = Some(r.asInstanceOf[FileResource])
          case _ =>
            logger.error(s"Jrxml reference loading failure: $path referenced in $uri")
            throw new ResourceNotFoundException(path)
        }
      }
    }
    else if (jrxmlFileCur.succeeded) {
      Resource(jrxmlFileCur, "file") match {
        case Right(r) if r.isInstanceOf[FileResource] => jrxml = Some(r.asInstanceOf[FileResource])
        case _ =>
          logger.error(s"Jrxml File loading failure $uri")
          jrxml = None
      }
    }

    var resourceCur = cur.downField("resources").downField("resource").downArray
    var resourceMap: Map[String, FileResource] = Map.empty

    while( resourceCur.succeeded ) {
      val name = resourceCur.downField("name").as[String].right.get
      val filePos = resourceCur.downField("file")
      val fileRefCur = filePos.downField("fileReference")
      val fileRscCur = filePos.downField("fileResource")
      if (fileRefCur.succeeded) {
        val ref = fileRefCur.downField("uri").as[String]
        if (ref.isRight){
          val path = ref.right.get
          implicit val ec: ExecutionContext = context.executionContext
          val future = context.repository.getResource(path)
          Await.result(future, 5.seconds) match {
            case Right(r) =>
              resourceMap ++= Map(name -> r.asInstanceOf[FileResource])
            case _ =>
              logger.error(s"Resource reference loading failure: $path referenced in $uri")
              throw new ResourceNotFoundException(path)
          }
        }
      }
      else if (fileRscCur.succeeded) {
        Resource(fileRscCur, "file") match {
          case Right(r) if r.isInstanceOf[FileResource] => resourceMap ++= Map(name -> r.asInstanceOf[FileResource])
          case _ =>
            logger.error(s"Resource reference loading failure in $uri")
        }
      }

      resourceCur = resourceCur.right
    }
    resources = resourceMap

    Right(this)
  }

  def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
    jrxml match {
      case Some(r) => context.repository.setResource(r.uri, r, createFolders = true, overwrite = true)
      case _ =>
    }

    resources.foreach{ case(name, r) =>
      context.repository.setResource(r.uri, r, createFolders = true, overwrite = true)
    }
  }
}


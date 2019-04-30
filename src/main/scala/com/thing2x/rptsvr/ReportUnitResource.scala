package com.thing2x.rptsvr

import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import io.circe.syntax._
import io.circe.{ACursor, DecodingFailure, Json}

class ReportUnitResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "reportUnit"
  override val mediaType: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("repository.reportUnit+json", HttpCharsets.`UTF-8`)

  var alwaysPromptControls: Boolean = true
  var controlsLayout: String = "popupScreen"
  var jrxml: Option[FileResource] = None
  var resources: Map[String, FileResource] = Map.empty
  var inputControls: Seq[InputControlResource] = Seq.empty

  override def encodeFields(expanded: Boolean): Map[String, Json] = Map(
    "alwaysPromptControls" -> Json.fromBoolean(alwaysPromptControls),
    "controlsLayout" -> Json.fromString(controlsLayout),
    "inputControls" -> inputControls.map( input =>
      if (expanded) {
        Json.obj("inputControl" -> input.asJson(expanded))
      }
      else {
        Json.obj("inputControlReference" -> Json.obj("uri" -> Json.fromString(input.uri)))
      }
    ).asJson,
    "jrxml" -> (
      if (jrxml.isDefined) {
        if (expanded) {
          Json.obj("jrxmlFile" -> jrxml.get.asJson(expanded))
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
              Json.obj( "fileResource" -> r.asJson(expanded))
            else
              Json.obj("fileReference" -> Json.obj("uri" -> Json.fromString(r.uri)))
            ))
        }.asJson
    )
  )

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    alwaysPromptControls = cur.downField("alwaysPromptControls").as[Boolean].right.get
    controlsLayout = cur.downField("controlsLayout").as[String].right.get

    decodeReferencedResource[FileResource](cur.downField("jrxml"), "jrxmlFile", "file") match {
      case Right(r) => jrxml = Some(r)
      case _ => jrxml = None
    }

    var resourceCur = cur.downField("resources").downField("resource").downArray
    var resourceMap: Map[String, FileResource] = Map.empty

    while( resourceCur.succeeded ) {
      val name = resourceCur.downField("name").as[String].right.get
      decodeReferencedResource[FileResource](resourceCur.downField("file"), "fileResource", "fileReference", "file") match {
        case Right(r) => resourceMap ++= Map(name -> r)
        case Left(e) =>  logger.error(s"Sub-resource loading failure ${e.message}")
      }
      resourceCur = resourceCur.right
    }
    resources = resourceMap

    var inputControlCur = cur.downField("inputControls").downArray
    var inputs: Seq[InputControlResource] = Seq.empty
    while( inputControlCur.succeeded ) {

      decodeReferencedResource[InputControlResource](inputControlCur, "inputControl", "inputControl") match {
        case Right(r) => inputs ++= Seq(r)
        case Left(e) => logger.error(s"Sub-input control loading failure ${e.message}")
      }
      inputControlCur = inputControlCur.right
    }
    inputControls = inputs

    Right(this)
  }

  def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
    jrxml match {
      case Some(r) => context.repository.setResource(r.uri, r, createFolders = true, overwrite = true)
      case _ =>
    }

    resources.foreach{ case(_, r) =>
      context.repository.setResource(r.uri, r, createFolders = true, overwrite = true)
    }

    inputControls.foreach { ic =>
      context.repository.setResource(ic.uri, ic, createFolders = true, overwrite = true)
    }
  }
}


package com.thing2x.rptsvr

import io.circe.{ACursor, DecodingFailure, Json}

class FileResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  var fileType: String = ""

  override val resourceType: String = "file"

  override def encodeFields(expanded: Boolean): Map[String, Json] =
    Map("type"->Json.fromString(fileType))

  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = {
    cur.downField("type").as[String] match {
      case Right(typ) => fileType = typ
      case _ => logger.error(s"resource $uri doesn't have 'type' meta info")
    }
    cur.downField("content").as[String] match {
      case Right(data) => content = Some(data)
      case _ => content = None
    }
    Right(this)
  }

  def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
    if (content.isDefined)
      writer.writeContent(content.get)
  }
}

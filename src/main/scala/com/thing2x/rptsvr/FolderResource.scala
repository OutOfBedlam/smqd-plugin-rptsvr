package com.thing2x.rptsvr

import io.circe.{ACursor, DecodingFailure, Json}

class FolderResource(val uri: String, val label: String)(implicit context: RepositoryContext) extends Resource {
  override val resourceType: String = "folder"
  override def encodeFields(expanded: Boolean): Map[String, Json] = Map.empty
  override def decodeFields(cur: ACursor): Either[DecodingFailure, Resource] = Right(this)

  def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }
}


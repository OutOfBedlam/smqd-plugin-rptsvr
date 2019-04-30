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

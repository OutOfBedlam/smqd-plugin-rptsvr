package com.thing2x.rptsvr

import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import io.circe.{ACursor, DecodingFailure, Json}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

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

    val dsRefCur = cur.downField("dataSource").downField("dataSourceReference")
    val dsValCur = cur.downField("dataSource").downField("dataSource")
    if (dsRefCur.succeeded) {
      val ref = dsRefCur.downField("uri").as[String]
      if (ref.isRight) {
        val path = ref.right.get
        val future = context.repository.getResource(path)
        Await.result(future, 5.seconds) match {
          case Right(r) =>
            dataSource = Some(r.asInstanceOf[DataSourceResource])
          case _ =>
            logger.error(s"DataSource reference loading failure: $path referenced in $uri")
            throw new ResourceNotFoundException(path)
        }
      }
    }
    else if (dsValCur.succeeded) {
      Resource(dsValCur, "dataSource") match {
        case Right(r) if r.isInstanceOf[DataSourceResource] => dataSource = Some(r.asInstanceOf[DataSourceResource])
        case _ =>
          logger.error(s"Jrxml File loading failure $uri")
          dataSource = None
      }
    }

    Right(this)
  }

  override def write(writer: ResourceWriter): Unit = {
    writer.writeMeta(this)
  }
}

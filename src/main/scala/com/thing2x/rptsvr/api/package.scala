package com.thing2x.rptsvr

import java.io.FileNotFoundException

import akka.http.scaladsl.model.{ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaType, ResponseEntity, StatusCode, StatusCodes}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}

import scala.language.implicitConversions

package object api {
  val `application/json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("json", HttpCharsets.`UTF-8`)

  val `application/repository.folder+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.folder+json", HttpCharsets.`UTF-8`)

  val `application/repository.file+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.file+json", HttpCharsets.`UTF-8`)

  val `application/repository.reportunit+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.reportunit+json", HttpCharsets.`UTF-8`)

  val `application/repository.jrxml+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.jrxml+json", HttpCharsets.`UTF-8`)

  val `application/repository.jdbcDataSource+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.jdbcdatasource+json", HttpCharsets.`UTF-8`)

  val `application/repository.resourceLookup+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.resourcelookup+json", HttpCharsets.`UTF-8`)


  def resourceMediaTypes: List[MediaType.WithFixedCharset] = List(
    `application/json`,
    `application/repository.folder+json`,
    `application/repository.file+json`,
    `application/repository.resourceLookup+json`,
    `application/repository.reportunit+json`,
    `application/repository.jdbcDataSource+json`,
  )

  def mediaTypeFromString(mediaType: String): MediaType = {
    val filtered = resourceMediaTypes.filter { mt =>
      s"${mt.mainType}/${mt.subType}".equalsIgnoreCase(mediaType)
    }

    if (filtered.isEmpty) `application/json` else filtered.head
  }

  implicit def asMediaType[T <: Resource](resource: T): MediaType.WithFixedCharset = {
    resource match {
      case _: FolderResource => `application/repository.folder+json`
      case fr: FileResource =>
        fr.`type` match {
          case "reportunit" => `application/repository.reportunit+json`
          case "jrxml" =>      `application/repository.jrxml+json`
          case _ =>            `application/json`
        }
      case _ => `application/json`
    }
  }

  implicit def asResponseEntity(resource: Resource): ResponseEntity = {
    HttpEntity(ContentType(resource), resource.asJson.noSpaces)
  }

  implicit def asHttpResponseFromResource(tup: (StatusCode, Resource)): HttpResponse = {
    asHttpResponseFromResource(tup._1, tup._2)
  }

  implicit def asHttpResponseFromResource(statusCode: StatusCode, resource: Resource): HttpResponse = {
    HttpResponse(statusCode, Nil, asResponseEntity(resource))
  }

  implicit def asHttpResponseFromJson(tup: (StatusCode, Json)): HttpResponse = {
    asHttpResponseFromJson(tup._1, tup._2)
  }

  implicit def asHttpResponseFromJson(statusCode: StatusCode, json: Json): HttpResponse = {
    HttpResponse(statusCode, Nil, HttpEntity(ContentType(`application/json`), json.noSpaces))
  }

  implicit def asHttpResponseFromResult[T <: Resource](tup: (StatusCode, Result[T])): HttpResponse = {
    asHttpResponseFromResult(tup._1, tup._2)
  }

  implicit def asHttpResponseFromResult[T <: Resource](successCode: StatusCode, result: Result[T]): HttpResponse = {
    result match {
      case Right(r) => asHttpResponseFromResource(successCode, r)
      case Left(exception) => exception match {
        case ex: ResourceNotFoundException => asHttpResponseFromException(StatusCodes.NotFound, ex)
        case ex: FileNotFoundException => asHttpResponseFromException(StatusCodes.NotFound, ex)
        case ex: ResourceAlreadyExistsExeption => asHttpResponseFromException(StatusCodes.BadRequest, ex)
        case _ => asHttpResponseFromException(StatusCodes.InternalServerError, exception)
      }
    }
  }

  implicit def asHttpResponseFromException(tup: (StatusCode, Throwable)): HttpResponse = {
    asHttpResponseFromException(tup._1, tup._2)
  }

  implicit def asHttpResponseFromException(statusCode: StatusCode, ex: Throwable): HttpResponse = {
    HttpResponse(statusCode, Nil, HttpEntity(ContentType(`application/json`), Json.obj(
      ("exception", Json.fromString(ex.toString))
    ).noSpaces))
  }

  implicit val resourceEncoder: Encoder[Resource] = new Encoder[Resource] {
    override def apply(resource: Resource): Json = {
      resource match {
        case r: FolderResource => r.asJson
        case r: FileResource => r.asJson
        case r: JdbcDataSourceResource => r.asJson
        case r: ReportUnitResource => r.asJson
        case _ => resource.asJson
      }
    }
  }
}

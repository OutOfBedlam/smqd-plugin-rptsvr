// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.thing2x.rptsvr

import java.io.FileNotFoundException

import akka.http.scaladsl.model._
import com.thing2x.rptsvr.Repository.{ResourceAlreadyExistsExeption, ResourceNotFoundException}
import com.thing2x.rptsvr.api.ResourceHandler.ResourceError
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.language.implicitConversions

package object api {
  // akka-http has issue to match range of content-type https://github.com/akka/akka-http/issues/2126
  // the specification reads it should comapre content type in case-insensitive way
  // but current version of akka-http do it in case-sensitive way
  // This should changed in the future version of akka-http
  val `application/json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("json", HttpCharsets.`UTF-8`)

  val `application/repository.folder+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.folder+json", HttpCharsets.`UTF-8`)

  val `application/repository.file+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.file+json", HttpCharsets.`UTF-8`)

  val `application/repository.reportUnit+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.reportunit+json", HttpCharsets.`UTF-8`)

  val `application/repository.jrxml+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.jrxml+json", HttpCharsets.`UTF-8`)

  val `application/repository.jdbcDataSource+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.jdbcdatasource+json", HttpCharsets.`UTF-8`)

  val `application/repository.resourceLookup+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.resourcelookup+json", HttpCharsets.`UTF-8`)

  val `application/repository.inputControl+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.inputcontrol+json", HttpCharsets.`UTF-8`)

  val `application/repository.dataType+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.datatype+json", HttpCharsets.`UTF-8`)

  val `application/repository.listOfValues+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.listOfValues+json", HttpCharsets.`UTF-8`)

  val `application/repository.query+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.query+json", HttpCharsets.`UTF-8`)

  def resourceMediaTypes: List[MediaType.WithFixedCharset] = List(
    `application/json`,
    `application/repository.folder+json`,
    `application/repository.file+json`,
    `application/repository.resourceLookup+json`,
    `application/repository.reportUnit+json`,
    `application/repository.jdbcDataSource+json`,
    `application/repository.inputControl+json`,
    `application/repository.dataType+json`,
    `application/repository.listOfValues+json`,
    `application/repository.query+json`,
  )

  def mediaTypeFromString(mediaType: String): MediaType = {
    val filtered = resourceMediaTypes.filter { mt =>
      s"${mt.mainType}/${mt.subType}".equalsIgnoreCase(mediaType)
    }

    if (filtered.isEmpty) `application/json` else filtered.head
  }

  implicit def asResponseEntity(resource: Resource)(implicit context: RepositoryContext): ResponseEntity = {
    HttpEntity(ContentType(resource.mediaType), resource.asJson.noSpaces)
  }

  implicit def asHttpResponseFromResource(tup: (StatusCode, Resource))(implicit context: RepositoryContext): HttpResponse = {
    asHttpResponseFromResource(tup._1, tup._2)
  }

  implicit def asHttpResponseFromResource(statusCode: StatusCode, resource: Resource)(implicit context: RepositoryContext): HttpResponse = {
    HttpResponse(statusCode, Nil, asResponseEntity(resource))
  }

  implicit def asHttpResponseFromJson(tup: (StatusCode, Json)): HttpResponse = {
    asHttpResponseFromJson(tup._1, tup._2)
  }

  implicit def asHttpResponseFromJson(statusCode: StatusCode, json: Json): HttpResponse = {
    HttpResponse(statusCode, Nil, HttpEntity(ContentType(`application/json`), json.noSpaces))
  }

  implicit def asHttpResponseFromResult[T <: Resource](tup: (StatusCode, Result[T], Boolean))(implicit context: RepositoryContext): HttpResponse = {
    asHttpResponseFromResult(tup._1, tup._2, tup._3)
  }

  implicit def asHttpResponseFromResult[T <: Resource](successCode: StatusCode, result: Result[T], expanded: Boolean)(implicit context: RepositoryContext): HttpResponse = {
    result match {
      case Right(r) => HttpResponse(successCode, Nil, HttpEntity(ContentType(r.mediaType), r.asJson(expanded).noSpaces))
      case Left(exception) => exception match {
        case _: ResourceNotFoundException => asHttpResponseFromResourceError(StatusCodes.NotFound, ResourceError("Resource not found.", "resource.not.found", Seq.empty))
        case ex: FileNotFoundException => asHttpResponseFromResourceError(StatusCodes.NotFound, ResourceError("Resource not found.", "resource.not.found", Seq.empty))
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

  implicit def asHttpResponseFromResourceError(statusCode: StatusCode, err: ResourceError): HttpResponse = {
    HttpResponse(statusCode, Nil, HttpEntity(ContentType(`application/json`), err.asJson.noSpaces))
  }
}

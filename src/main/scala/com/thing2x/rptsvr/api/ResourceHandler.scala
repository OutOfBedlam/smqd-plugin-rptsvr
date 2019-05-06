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

package com.thing2x.rptsvr.api

import akka.http.scaladsl.model._
import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

object ResourceHandler {
  case class ResourceError(message: String, errorCode: String, parameters: Seq[String])
}

class ResourceHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends StrictLogging {

  private val repo = Repository.instance.get

  private implicit val repoContext: RepositoryContext = repo.context

  def lookupResource(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[HttpResponse] = {
    logger.debug(s"lookup resource >> $path recursive=$recursive sortBy=$sortBy limit=$limit")

    repo.listFolder(path, recursive, sortBy, limit).map {
      case Right(list) =>
        val result = list.map( r => r.asLookupResult )
        (StatusCodes.OK, Json.obj("resourceLookup" -> result.asJson))
      case Left(ex) =>
        logger.warn(s"lookupResource failure: $path ", ex)
        (StatusCodes.InternalServerError, ex)
    }
  }

  def getResource(path: String, expanded: Option[Boolean]): Future[HttpResponse] = {
    if (expanded.isDefined) {
      logger.debug(s"get resource >> $path expanded=${expanded.get}")
      repo.getResource(path).map( (StatusCodes.OK, _, expanded.get) )
    }
    else {
      logger.debug(s"get content  >> $path")
      repo.getContent(path).map{
        case Right(cr) => HttpResponse(StatusCodes.OK, Nil, HttpEntity(cr.contentType, cr.source))
        case Left(ex)  =>
          logger.warn(s"get content failure: $path", ex)
          (StatusCodes.InternalServerError, ex)
      }
    }
  }

  def setResource(path: String, contentType: ContentType, json: Json, createFolders: Boolean, overwrite: Boolean): Future[HttpResponse] = {
    logger.trace(s"set resource >> $path ${contentType.toString} ${json.spaces2}")
    val mediaType = contentType.mediaType
    val subType = mediaType.subType
    if (mediaType.isApplication && subType.startsWith("repository.") && subType.endsWith("+json")) {
      val resourceType = subType.substring("repository.".length, subType.lastIndexOf("+json"))
      logger.trace(s"resourceType='$resourceType' path=$path")
      Resource(json, resourceType) match {
        case Right(resource) =>
          repo.setResource(path, resource, createFolders, overwrite).map( (StatusCodes.Created, _, true) )
        case Left(failure) =>
          Future{
            val err = s"Error can not parse resource as $resourceType, $failure"
            logger.error(err)
            (StatusCodes.BadRequest, Json.obj(("error", Json.fromString(err))))
          }
      }
    }
    else {
      Future{
        logger.error(s"Unhandled content type: ${contentType.toString}")
        (StatusCodes.BadRequest, Json.obj(("error", Json.fromString(s"Unhandled content type: $contentType"))))
      }
    }
  }

  def deleteResource(path: String): Future[HttpResponse] = {
    repo.deleteResource(path).map { success =>
      if (success) {
        (StatusCodes.OK, Json.obj(("success", Json.fromBoolean(success))))
      }
      else {
        (StatusCodes.InternalServerError, Json.obj(("error", Json.fromString(s"can not delete resource $path"))))
      }
    }
  }
}

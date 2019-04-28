package com.thing2x.rptsvr.api

import akka.http.scaladsl.model._
import com.thing2x.rptsvr.Repository.ResourceLookupResponse
import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

class ResourceHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends StrictLogging {

  private val repo = Repository.findInstance(smqd)

  def lookupResource(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[HttpResponse] = {
    logger.debug(s"lookup resource >> $path recursive=$recursive sortBy=$sortBy limit=$limit")

    repo.listFolder(path, recursive, sortBy, limit).map {
      case Right(list) =>
        val result = ResourceLookupResponse(list)
        (StatusCodes.OK, result.asJson)
      case Left(ex) =>
        logger.warn(s"lookupResource failure: $path ", ex)
        (StatusCodes.InternalServerError, ex)
    }
  }

  def getResource(path: String, accept: MediaType, expanded: Option[Boolean]): Future[HttpResponse] = {
    logger.debug(s"get resource >> $path expanded=$expanded accept=$accept")
    accept match {
      case `application/repository.resourceLookup+json` =>
        repo.getResource(path).map( (StatusCodes.OK, _) )
      case `application/repository.folder+json` =>
        repo.getResource(path).map( (StatusCodes.OK, _) )
      case `application/repository.file+json` =>
        if (expanded.isDefined) {
          repo.getResource(path).map( (StatusCodes.OK, _) )
        }
        else {
          repo.getContent(path).map(cr => HttpResponse(StatusCodes.OK, Nil, HttpEntity.fromFile(cr.contentType, cr.file)))
        }
      case _ =>
        if (expanded.isDefined) {
          repo.getResource(path).map( (StatusCodes.OK, _) )
        }
        else {
          repo.getContent(path).map(cr => HttpResponse(StatusCodes.OK, Nil, HttpEntity.fromFile(cr.contentType, cr.file)))
        }
    }
  }

  def setResource(path: String, contentType: ContentType, body: Config, createFolders: Boolean, overwrite: Boolean): Future[HttpResponse] = {
    logger.trace(s"write resource >> $path ${contentType.toString} ${body.root.render(ConfigRenderOptions.concise)}")
    val mediaType = contentType.mediaType
    val subType = mediaType.subType
    if (mediaType.isApplication && subType.startsWith("repository.") && subType.endsWith("+json")) {
      val resourceType = subType.substring("repository.".length, subType.lastIndexOf("+json"))
      repo.setResource(path, body, createFolders, overwrite, resourceType).map( (StatusCodes.Created, _) )
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

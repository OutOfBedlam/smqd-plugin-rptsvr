package com.thing2x.rptsvr.api

import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.util.ByteString
import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, parser}

import scala.concurrent.{ExecutionContext, Future}

object ResourceHandler {
  def findRepositoryInstance(smqd: Smqd): Repository = {
    val repositoryClass = classOf[Repository]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      repositoryClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[Repository]).get
  }
}

class ResourceHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends StrictLogging {

  private val repo = ResourceHandler.findRepositoryInstance(smqd)

  def lookupResource(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[HttpResponse] =
    repo.listFolder(path, recursive, sortBy, limit).map { r =>
      val result = ResourceLookupResponse(r)
      (StatusCodes.OK, result.asJson)
    }

  def getResource(path: String, expanded: Boolean, accept: MediaType): Future[HttpResponse] = {
    logger.debug(s"get resource >> $path expanded=$expanded accept=$accept")
    accept match {
      case `application/repository.folder+json` =>
        repo.getFolder(path).map( (StatusCodes.OK, _) )
      case `application/repository.resourceLookup+json` =>
        repo.getResource(path, expanded).map( (StatusCodes.OK, _) )
      case _ =>
        repo.getResource(path, expanded).map( (StatusCodes.OK, _) )
    }
  }

  def createResource(path: String, content: RequestEntity, createFolders: Boolean, overwrite: Boolean)
                    (implicit materializr: Materializer): Future[HttpResponse] = {
    content.dataBytes.runFold(ByteString.empty)( _ ++ _ ).map { bstr =>
      logger.debug(s"create resource >> ${bstr.utf8String}")
      parser.parse(bstr.utf8String).right.get
    }.flatMap{ json =>
      content.contentType.mediaType match {
        case `application/repository.folder+json` =>
          val req = json.as[CreateFolderRequest].right.get
          repo.createFolder(req).map( (StatusCodes.OK, _) )

        case `application/repository.file+json` =>
          val req = json.as[CreateFileRequest].right.get
          repo.createFile(req, createFolders, overwrite).map( (StatusCodes.OK, _) )

//        case `application/repository.jdbcDataSource+json` =>
//          val ds = json.as[DSJdbcResource].right.get
//          storeResource(path, ds, createFolders, overwrite).map( (StatusCodes.OK, _) )

        case ct =>
          Future{
            (StatusCodes.InternalServerError, Json.obj(("error", Json.fromString(s"Unhandled content type: $ct"))))
          }
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

package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.{MediaType, RequestEntity, StatusCode, StatusCodes}
import akka.stream.Materializer
import akka.util.ByteString
import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, parser}

import scala.concurrent.{ExecutionContext, Future}

object ResourceHandler extends ResourceMediaTypes {
  def findRepositoryInstance(smqd: Smqd): Repository = {
    val repositoryClass = classOf[Repository]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      repositoryClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[Repository]).get
  }
}

class ResourceHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends ResourceCodec with ResourceMediaTypes with StrictLogging {

  private val repo = ResourceHandler.findRepositoryInstance(smqd)

  def listFolder(folderUri: String, recursive: Boolean, sortBy: String, limit: Int): Future[(StatusCode, Json)] =
    repo.listFolder(folderUri, recursive, sortBy, limit).map( r => (StatusCodes.OK, Json.obj(("resourceLookup", r.asJson))) )

  def storeResource(path: String, ds: DSJdbcResource, createFolders: Boolean, overwrite: Boolean): Future[DSJdbcResource] = Future {
    logger.debug(s"path=$path createFolders=$createFolders overwrite=$overwrite $ds")
    //repo.store(path)
    ds
  }

  def getResource(path: String, expanded: Boolean, accept: MediaType): Future[(StatusCode, Json)] = {
    logger.debug(s"get resource >> $path expanded=$expanded accept=$accept")
    accept match {
      case `application/repository.folder+json` =>
        repo.getFolder(path).map(r => (StatusCodes.OK, r.asJson))
      case `application/repository.resourceLookup+json` =>
        repo.getResource(path, expanded).map(r => (StatusCodes.OK, r.asJson))
      case _ =>
        repo.getResource(path, expanded).map(r => (StatusCodes.OK, r.asJson))
    }
  }

  def createResource(path: String, content: RequestEntity, createFolders: Boolean, overwrite: Boolean)
                    (implicit materializr: Materializer): Future[(StatusCode, Json)] = {
    content.dataBytes.runFold(ByteString.empty)( _ ++ _ ).map { bstr =>
      logger.debug(s"create resource >> ${bstr.utf8String}")
      parser.parse(bstr.utf8String).right.get
    }.flatMap{ json =>
      content.contentType.mediaType match {
        case `application/repository.folder+json` =>
          val req = json.as[CreateFolderRequest].right.get
          repo.createFolder(req).map(r => (StatusCodes.OK, r.asJson))

        case `application/repository.file+json` =>
          val req = json.as[CreateFileRequest].right.get
          repo.createFile(req, createFolders, overwrite).map(r => (StatusCodes.OK, r.asJson))

        case `application/repository.jdbcDataSource+json` =>
          val ds = json.as[DSJdbcResource].right.get
          storeResource(path, ds, createFolders, overwrite).map(r => (StatusCodes.OK, r.asJson))

        case ct =>
          Future(StatusCodes.InternalServerError, Json.obj(("error", Json.fromString(s"Unhandled content type: $ct"))))
      }
    }
  }

  def deleteResource(path: String): Future[(StatusCode, Json)] = {
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

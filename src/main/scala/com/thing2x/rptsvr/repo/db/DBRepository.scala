package com.thing2x.rptsvr.repo.db

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.thing2x.rptsvr.Repository.ResourceNotFoundException
import com.thing2x.rptsvr.api.MimeTypes
import com.thing2x.rptsvr.{FileContent, FileResource, FolderResource, JdbcDataSourceResource, ListResult, QueryResource, ReportUnitResource, Repository, RepositoryContext, Resource, Result}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class DBRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging
  with ResourceFolderTableSupport
  with ResourceTableSupport
  with FileResourceTableSupport
  with DataSourceSupport
  with JdbcDataSourceTableSupport
  with QueryTableSupport
  with ReportUnitTableSupport {

  protected implicit val dbContext: DBRepositoryContext =
    new DBRepositoryContext(this, smqd, config)

  override val context: RepositoryContext = dbContext

  protected implicit val ec: ExecutionContext = context.executionContext
  protected implicit val materializer: Materializer = dbContext.materializer

  override def start(): Unit = {
    dbContext.open() {
      logger.info("Deferred actions")
    }

    if (!dbContext.readOnly)
      DBSchema.createSchema(dbContext)
  }

  override def stop(): Unit = {
    dbContext.close()
  }

  private[db] def splitPath(path: String): (String, String) = {
    val paths = path.split('/')
    val parentFolderPath = paths.dropRight(1).mkString("/", "/", "").replaceAllLiterally("//", "/")
    val name = paths.last
    (parentFolderPath, name)
  }

  override def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]] = {
    for {
      fl <- selectSubFoldersFromResourceFolder(path).map(_.map( _.asApiModel ))
      rl <- selectResourcesFromResourceFolder(path).map( _.map { r => r.resourceType match {
        case JIResourceTypes.reportUnit =>
          ReportUnitResource(path + "/" + r.name, r.label)
        case JIResourceTypes.jdbcDataSource =>
          FileResource(path + "/" + r.name, r.label)
        case JIResourceTypes.file =>
          FileResource(path + "/" + r.name, r.label)
        case _ =>
          FileResource(path + "/" + r.name, r.label)
      }})
    } yield fl ++ rl
  }

  override def setResource(path: String, request: Resource, createFolders: Boolean, overwrite: Boolean): Future[Result[Resource]] = {
    logger.debug(s"setResource uri=$path, $request, resourceType=${request.resourceType}")
    val result = request match {
      case req: FolderResource =>          insertResourceFolder(req).flatMap( selectResourceFolder )
      case req: FileResource =>            insertFileResource(req).flatMap( selectFileResource )
      case req: JdbcDataSourceResource =>  insertJdbcDataSourceResource(req).flatMap( selectJdbcDataSourceResource )
      case req: QueryResource =>           insertQueryResource(req).flatMap( selectQueryResource )
    }
    result.map {
      case r: JIResourceFolder => Right(r.asApiModel)
      case r: JIResourceObject => Right(r.asApiModel)
      case ex: Throwable =>
        logger.error(s"resource not found", ex)
        Left(new ResourceNotFoundException(path))
      case other =>
        logger.error(s"Unhandled db result $other")
        Left(new ResourceNotFoundException(path))
    }
  }

  override def getResource(path: String, isReferenced: Boolean): Future[Result[Resource]] = {
    logger.debug(s"getResource uri=$path, isReferenced=$isReferenced")
    // find folder with path, if it is not a folder use path as resource
    selectResourceFolder(path).map { f =>
      Right(f.asApiModel)
    }.recoverWith {
      case ex: Throwable =>
        selectResource(path).flatMap { resource =>
          resource.resourceType match {
            case JIResourceTypes.file =>              selectFileResource(resource.id).map( _.asApiModel )
            case JIResourceTypes.jdbcDataSource =>    selectJdbcDataSourceResource(resource.id).map( _.asApiModel )
            case JIResourceTypes.query =>             selectQueryResource(resource.id).map( _.asApiModel )
            // TODO:
//            case JIResourceTypes.reportUnit => ???
            case _ => ???
          }

        }
    }
  }

  override def getContent(path: String): Future[Either[Throwable, FileContent]] = {
    selectFileResource(path).map { ro =>
      val file = ro.obj.asInstanceOf[JIFileResource]
      val src = file.data.map( d => Source.fromFuture(Future( ByteString(d) )) ).getOrElse(Source.empty)
      Right(FileContent(path, src, MimeTypes.mimeTypeOf( file.fileType, ro.resource.name )))
    }
  }

  override def deleteResource(path: String): Future[Boolean] = ???
}

package com.thing2x.rptsvr.repo.db

import java.util.Base64

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.thing2x.rptsvr.Repository.ResourceNotFoundException
import com.thing2x.rptsvr.api.MimeTypes
import com.thing2x.rptsvr.repo.db.DBRepository._
import com.thing2x.rptsvr.{DataSourceResource, FileContent, FileResource, FolderResource, JdbcDataSourceResource, ListResult, QueryResource, ReportUnitResource, Repository, RepositoryContext, Resource, Result}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.sql.rowset.serial.{SerialBlob, SerialClob}
import org.slf4j.LoggerFactory
import slick.dbio.DBIOAction
import slick.jdbc.H2Profile.api._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object DBRepository {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val resources = TableQuery[JIResourceTable]
  val resourceInsert = resources returning resources.map(_.id)

  val resourceFolders = TableQuery[JIResourceFolderTable]
  val resourceFolderInsert = resourceFolders returning resourceFolders.map(_.id)

  val fileResources = TableQuery[JIFileResourceTable]
  val jdbcResources = TableQuery[JIJdbcDatasourceTable]
  val queryResources = TableQuery[JIQueryTable]

  private val schema = Seq(
    resourceFolders.schema,
    resources.schema,
    fileResources.schema,
    jdbcResources.schema,
    queryResources.schema,
  )
}

class DBRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging {

  private implicit val dbContext: DBRepositoryContext =
    new DBRepositoryContext(this, smqd, config)

  override val context: RepositoryContext = dbContext

  private implicit val ec: ExecutionContext = context.executionContext
  private implicit val materializer: Materializer = dbContext.materializer

  override def start(): Unit = {
    dbContext.open() {
      logger.info("Deferred actions")
    }

    if (!dbContext.readOnly)
      createSchema(dbContext)
  }

  override def stop(): Unit = {
    dbContext.close()
  }

  private def createSchema(ctx: DBRepositoryContext): Unit = {
    val setup = for {
      // create tables
      _                <- schema.reduce( _ ++ _).create
      // insert default tables
      rootFolderId     <- resourceFolderInsert += JIResourceFolder("/", "/", "/", Some("Root"), -1)
      orgId            <- resourceFolderInsert += JIResourceFolder("/organizations", "organizations", "Organizations", Some("Oraganizations"), rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/public", "public", "Public", None, rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/temp", "temp", "Temp", Some("Temp"), rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/organizations/organization_1", "organization_1", "Organization", None, orgId)
    } yield rootFolderId

    dbContext.runSync(setup, 8.second)
  }

  private def splitPath(path: String): (String, String) = {
    val paths = path.split('/')
    val parentFolderPath = paths.dropRight(1).mkString("/", "/", "").replaceAllLiterally("//", "/")
    val name = paths.last
    (parentFolderPath, name)
  }

  override def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]] = {
    val folderListQuery = for {
      folderId <- resourceFolders.filter(_.uri === path).map(_.id)
      childFolders <- resourceFolders.filter(_.parentFolder === folderId)
    } yield childFolders

    val childFolderList = dbContext.database.run(folderListQuery.result).map { rset =>
      rset.map(f => FolderResource(f.uri, f.label))
    }

    val resourceListQuery = for {
      folderId <- resourceFolders.filter(_.uri === path).map(_.id)
      rscs     <- resources.filter(_.parentFolder === folderId)
    } yield rscs

    val resourceList = dbContext.database.run(resourceListQuery.result).map { rset =>
      rset.map{ f =>
        f.resourceType match {
          case JIResourceTypes.reportUnit =>
            ReportUnitResource(path+"/"+f.name, f.label)
          case JIResourceTypes.jdbcDataSource =>
            FileResource(path+"/"+f.name, f.label)
          case JIResourceTypes.file =>
            FileResource(path+"/"+f.name, f.label)
          case _ =>
            FileResource(path+"/"+f.name, f.label)
        }
      }
    }

    for {
      fl <- childFolderList
      rl <- resourceList
    } yield fl ++ rl
  }

  override def setResource(path: String, request: Resource, createFolders: Boolean, overwrite: Boolean): Future[Result[Resource]] = {
    logger.debug(s"setResource uri=$path, $request, resourceType=${request.resourceType}")
    val result = request match {
      case req: FolderResource =>          insertFolder(req)
      case req: FileResource =>            insertFile(req)
      case req: JdbcDataSourceResource =>  insertJdbcDataSource(req)
      case req: QueryResource =>           insertQuery(req)
    }
    result.map {
      case Some(r) => Right(r)
      case _ => Left(new ResourceNotFoundException(path))
    }
  }

  override def getResource(path: String, isReferenced: Boolean): Future[Result[Resource]] = {
    logger.debug(s"getResource uri=$path, isReferenced=$isReferenced")
    // find folder with path, if it is not a folder use path as resource
    selectFolder(path).flatMap {
      case Some(folder) => Future( Right(folder) )
      case _ => selectResource(path, isReferenced).map {
        case Some(resource) => Right(resource)
        case _ => Left( new ResourceNotFoundException(path) )
      }
    }
  }

  override def getContent(path: String): Future[Either[Throwable, FileContent]] = {
    val (parentFolderPath, name) = splitPath(path)
    val resourceAction = for {
      parentFolder <- resourceFolders.filter( _.uri === parentFolderPath )
      resource     <- resources.filter( _.parentFolder === parentFolder.id).filter( _.name === name )
      content      <- fileResources.filter ( _.id === resource.id )
    } yield (resource, content)

    val rset = dbContext.database.run(resourceAction.result.headOption)
    val rt = rset.map {
      case Some((resource, content)) =>
        val contentAction = fileResources.filter( _.id === resource.id ).map( _.data ).take(1)
        val stream = dbContext.database.stream(contentAction.result).mapResult{
          case Some(data) => ByteString(data.getBytes(0, data.length.toInt))
          case _ =>          ByteString.empty
        }
        val src = Source.fromPublisher(stream)
        Right(FileContent(path, src, MimeTypes.mimeTypeOf( content.fileType, resource.name )))
      case _ =>
        Left(new ResourceNotFoundException(path))
    }
    rt
  }

  override def deleteResource(path: String): Future[Boolean] = ???

  private def selectResource(id: Long, isReferenced: Boolean): Future[Option[Resource]] = {
    selectResource0(Right(id), isReferenced)
  }
  private def selectResource(path: String, isReferenced: Boolean): Future[Option[Resource]] = {
    selectResource0(Left(path), isReferenced)
  }

  private def selectResource0(pathOrId: Either[String, Long], isReferenced: Boolean): Future[Option[Resource]] = {
    selectResourceMeta(pathOrId).flatMap{
      case Some(meta) =>
        meta.resourceType match {
          case JIResourceTypes.file =>              selectFile(meta)
          case JIResourceTypes.jdbcDataSource =>    selectJdbcDataSource(meta)
          case JIResourceTypes.query =>             selectQuery(meta, isReferenced)
          case JIResourceTypes.reportUnit => ???
          case _ => ???
        }
      case _ =>
        Future(None)
    }
  }

  private case class ResourceMeta(id: Long, name: String, label: String, resourceType: String, version: Int, uri: String,
                                  parentFolderId: Long, parentFolderUri: String)

  private def selectResourceMeta(pathOrId: Either[String, Long]): Future[Option[ResourceMeta]] = {
    logger.trace(s"selectResourceMeta $pathOrId")
    val action = pathOrId match {
      case Right(id) =>
        for {
          resource <- resources.filter(_.id === id)
          parentFolder <- resource.parentFolderFk
        } yield (resource, parentFolder)
      case Left(path) =>
        val (parentFolderPath, name) = splitPath(path)
        for {
          parentFolder <- resourceFolders.filter(_.uri === parentFolderPath)
          resource <- resources.filter(_.parentFolder === parentFolder.id).filter(_.name === name)
        } yield (resource, parentFolder)
    }
    dbContext.run(action.result.headOption).map {
      case Some((resource, folder)) =>
        logger.trace(s"selectResourceMeta $pathOrId $resource $folder")
        Some(ResourceMeta(resource.id, resource.name, resource.label, resource.resourceType, resource.version,
          s"${folder.uri}/${resource.name}", folder.id, folder.uri))
      case _ =>
        logger.debug(s"selectResourceMeta $pathOrId not found")
        None
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Folder
  /////////////////////////////////////////////////////////////////////////////

  private def selectFolder(path: String): Future[Option[FolderResource]] = {
    selectFolder0(Left(path))
  }

  private def selectFolder(id: Long): Future[Option[FolderResource]] = {
    selectFolder0(Right(id))
  }

  private def selectFolder0(pathOrId: Either[String, Long]): Future[Option[FolderResource]] = {
    val action = pathOrId match {
      case Left(path) => resourceFolders.filter(_.uri === path)
      case Right(id) => resourceFolders.filter(_.id === id)
    }

    logger.trace(s"selectFolder $pathOrId ${action.result.statements.mkString}")

    dbContext.run(action.result.headOption).map {
      case Some(r) =>
        val fr = FolderResource(r.uri, r.label)
        fr.version = r.version
        fr.permissionMask = 1
        Some(fr)
      case _ =>
        None
    }
  }

  private def insertFolder(request: FolderResource): Future[Option[FolderResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val folderAction = for {
      parentFolder <- resourceFolders.filter(_.uri === parentFolderPath).result.head
      folderId     <- resourceFolderInsert += JIResourceFolder(request.uri, name, request.label, request.description, parentFolder.id, version = request.version + 1)
    } yield folderId
    dbContext.run(folderAction).flatMap { r =>
      selectFolder(r) map {
        case Some(f) => Some(f)
        case _ => None
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // File
  /////////////////////////////////////////////////////////////////////////////

  private def selectFile(meta: ResourceMeta): Future[Option[FileResource]] = {
    meta.resourceType match {
      case JIResourceTypes.file =>
        val subact = fileResources.filter(_.id === meta.id)
        dbContext.run(subact.result.head).map{ r =>
          val fr = FileResource(meta.uri, meta.label)
          fr.version = meta.version
          fr.permissionMask = 1
          fr.fileType = r.fileType
          Some(fr)
        }
      case _ =>
        Future( None )
    }
  }

  private def insertFile(request: FileResource): Future[Option[FileResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val action = for {
      parentFolder <- resourceFolders.filter(_.uri === parentFolderPath).result.head
      fileId       <- resourceInsert += JIResource(name, parentFolder.id, None, request.label, request.description, JIResourceTypes.file, version = request.version + 1)
      _            <- fileResources += JIFileResource(request.fileType, request.content.map{ ctnt => new SerialBlob(Base64.getDecoder.decode(ctnt)) }, None, fileId)
    } yield fileId
    dbContext.run(action).flatMap { r =>
      selectResource(r, isReferenced = true) map {
        case Some(file) => Some(file.asInstanceOf[FileResource])
        case _ => None
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // JdbcDataSource
  /////////////////////////////////////////////////////////////////////////////

  private def selectJdbcDataSource(meta: ResourceMeta): Future[Option[JdbcDataSourceResource]] = {
    meta.resourceType match {
      case JIResourceTypes.jdbcDataSource =>
        val subact = jdbcResources.filter(_.id === meta.id)
        dbContext.run(subact.result.head).map{ r =>
          val fr = JdbcDataSourceResource(meta.uri, meta.label)
          fr.version = meta.version
          fr.permissionMask = 1
          fr.driverClass = Some(r.driver)
          fr.connectionUrl = r.connectionUrl
          fr.username = r.username
          fr.password = r.password
          fr.timezone = r.timezone
          Some(fr)
        }
      case _ =>
        Future( None )
    }
  }

  private def insertJdbcDataSource(request: JdbcDataSourceResource): Future[Option[JdbcDataSourceResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val action = for {
      parentFolder <- resourceFolders.filter(_.uri === parentFolderPath).result.head
      fileId       <- resourceInsert += JIResource(name, parentFolder.id, None, request.label, request.description, JIResourceTypes.jdbcDataSource)
      _            <- jdbcResources += JIJdbcDatasource(request.driverClass.get, request.connectionUrl, request.username, request.password, request.timezone, fileId)
    } yield fileId
    dbContext.run(action).flatMap { r =>
      selectResource(r, isReferenced = true) map {
        case Some(jdbc) => Some(jdbc.asInstanceOf[JdbcDataSourceResource])
        case _ => None
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Query
  /////////////////////////////////////////////////////////////////////////////

  private def selectQuery(meta: ResourceMeta, isReferenced: Boolean): Future[Option[QueryResource]] = {
    meta.resourceType match {
      case JIResourceTypes.query =>
        val subact = for {
          query <- queryResources.filter(_.id === meta.id)
        } yield query

        val publisher = dbContext.database.stream(subact.result).mapResult { r =>
          val fr = QueryResource(meta.uri, meta.label)
          fr.version = meta.version
          fr.permissionMask = 1
          fr.query = r.sqlQuery.getSubString(1, r.sqlQuery.length.toInt)
          fr.language = r.queryLanguage
          fr.dataSource = r.dataSource.flatMap { dsId =>
            Await.result(selectResource(dsId, isReferenced), 3.seconds).map(_.asInstanceOf[DataSourceResource])
          }
          fr
        }
        Source.fromPublisher(publisher).runFold(None.asInstanceOf[Option[QueryResource]])((_, b) => Some(b))
      case _ =>
        Future( None )
    }
  }

  private def insertQuery(request: QueryResource): Future[Option[QueryResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val dataSourceId = request.dataSource match {
      case Some(ds) =>
        val (parentPath, name) = splitPath(ds.uri)
        val dsAction = for {
          parentFolder <- resourceFolders.filter(_.uri === parentPath)
          dsRscId      <- resources.filter(_.parentFolder === parentFolder.id).filter(_.name === name).map(_.id)
        } yield dsRscId
        dbContext.run(dsAction.result.headOption)
      case _ => Future(None)
    }
    val action = for {
      dsId         <- DBIOAction.from(dataSourceId)
      parentFolder <- resourceFolders.filter(_.uri === parentFolderPath).result.head
      fileId       <- resourceInsert += JIResource(name, parentFolder.id, None, request.label, request.description, JIResourceTypes.query)
      _            <- queryResources += JIQuery(request.language, new SerialClob(request.query.toCharArray), dsId, fileId)
    } yield fileId
    dbContext.run(action).flatMap { r =>
      selectResource(r, isReferenced = true) map {
        case Some(query) => Some(query.asInstanceOf[QueryResource])
        case _ => None
      }
    }
  }
}

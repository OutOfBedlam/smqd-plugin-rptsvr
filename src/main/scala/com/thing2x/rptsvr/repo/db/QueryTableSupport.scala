package com.thing2x.rptsvr.repo.db
import com.thing2x.rptsvr.QueryResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait QueryTableSupport { mySelf: DBRepository =>
  /*
  def selectQuery(meta: ResourceMeta, isReferenced: Boolean): Future[Option[QueryResource]] = {
    meta.resourceType match {
      case JIResourceTypes.query =>
        val subact = for {
          query <- queryResources.filter(_.id === meta.id)
        } yield query

        val queryPublisher = dbContext.database.stream(subact.result).mapResult { r =>
          val fr = QueryResource(meta.uri, meta.label)
          fr.version = meta.version
          fr.permissionMask = 1
          fr.query = r.sqlQuery.getSubString(1, r.sqlQuery.length.toInt)
          fr.language = r.queryLanguage
          fr.dataSource = None
          (fr, r.dataSource)
        }

        val f = Source.fromPublisher(queryPublisher).runFold(None.asInstanceOf[Option[(QueryResource, Option[Long])]])((_, b) => Some(b))

        val futureResult = for {
          queryResource <- f.map {
            case Some((queryResource, _)) => Some(queryResource)
            case _ => None
          }
          dsFuture <- f.map{
            case Some((_, dsIdOpt)) => dsIdOpt match {
              case Some(dsId) => selectResource(dsId, isReferenced).map(_.asInstanceOf[Option[DataSourceResource]])
              case _ => Future( None )
            }
            case _ => Future( None )
          }
          ds <- dsFuture
        } yield (queryResource, ds)

        futureResult.map{
          case (Some(queryResource), ds) =>
            queryResource.dataSource = ds
            Some(queryResource)
          case _ => None
        }
      case _ =>
        Future( None )
    }
  }
   */

  /*
  def insertQuery(request: QueryResource): Future[Option[QueryResource]] = {
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
   */

  def selectQueryResource(path: String): Future[JIResourceObject] = selectQueryResource(Left(path))

  def selectQueryResource(id: Long): Future[JIResourceObject] = selectQueryResource(Right(id))

  private def selectQueryResource(pathOrId: Either[String, Long]): Future[JIResourceObject] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          query    <- queryResources.filter(_.id === resource.id)
        } yield (folder, resource, query)
      case Right(id) =>
        for {
          query   <- queryResources.filter(_.id === id)
          resource <- query.idFk
          folder   <- resource.parentFolderFk
        } yield (folder, resource, query)
    }

    dbContext.run(action.result.head).map( JIResourceObject(_) )
  }

  def insertQueryResource(request: QueryResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)

    val dsFuture: Future[Option[Long]] = request.dataSource match {
      case Some(ds) => selectResource( ds.uri ).map( _.id ).map( Some(_) )
      case None => Future( None )
    }

    for {
      dsId            <- dsFuture
      parentFolderId  <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId      <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, JIResourceTypes.file, version = request.version + 1 ))
      queryResourceId <- insertQueryResource( JIQuery(request.language, request.query, dsId, resourceId) )
    } yield queryResourceId
  }

  def insertQueryResource(query: JIQuery): Future[Long] = {
    val action = queryResources += query
    dbContext.run(action).map( _ => query.id )
  }
}

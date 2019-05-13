package com.thing2x.rptsvr.repo.db
import com.thing2x.rptsvr.QueryResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait QueryTableSupport { mySelf: DBRepository =>

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

package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.JdbcDataSourceResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait JdbcDataSourceTableSupport { mySelf: DBRepository =>

  def selectJdbcDataSourceResource(path: String): Future[JIResourceObject] = selectJdbcDataSourceResource(Left(path))

  def selectJdbcDataSourceResource(id: Long): Future[JIResourceObject] = selectJdbcDataSourceResource(Right(id))

  private def selectJdbcDataSourceResource(pathOrId: Either[String, Long]): Future[JIResourceObject] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          dsResource <- jdbcResources.filter(_.id === resource.id)
        } yield (folder, resource, dsResource)

      case Right(id) =>
        for {
          dsResource <- jdbcResources.filter(_.id === id)
          resource   <- dsResource.idFk
          folder     <- resource.parentFolderFk
        } yield(folder, resource, dsResource)
    }

    dbContext.run(action.result.head).map( JIResourceObject(_) )
  }

  def insertJdbcDataSourceResource(request: JdbcDataSourceResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, JIResourceTypes.jdbcDataSource, version = request.version + 1))
      jdbcResourceId <- insertJdbcDataSourceResource( JIJdbcDatasource(request.driverClass.get, request.connectionUrl, request.username, request.password, request.timezone, resourceId) )
    } yield jdbcResourceId
  }

  def insertJdbcDataSourceResource(jdbc: JIJdbcDatasource): Future[Long] = {
    val action = jdbcResources += jdbc
    dbContext.run(action).map( _ => jdbc.id )
  }
}

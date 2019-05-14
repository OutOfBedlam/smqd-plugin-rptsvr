package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.repo.db.DBSchema._
import com.thing2x.rptsvr.{DataSourceResource, JdbcDataSourceResource}
import slick.lifted.ProvenShape

import scala.concurrent.Future

import DBSchema.profile.api._

// create table JIJdbcDatasource (
//        id number(19,0) not null,
//        driver nvarchar2(100) not null,
//        password nvarchar2(250),
//        connectionUrl nvarchar2(500),
//        username nvarchar2(100),
//        timezone nvarchar2(100),
//        primary key (id)
//    );
final case class JIJdbcDatasource( driver: String,
                                   connectionUrl: Option[String],
                                   username: Option[String],
                                   password: Option[String],
                                   timezone: Option[String],
                                   id: Long = 0L)

final class JIJdbcDatasourceTable(tag: Tag) extends Table[JIJdbcDatasource](tag, "JIJdbcDatasource") {
  def driver = column[String]("driver")
  def connectionUrl = column[Option[String]]("connectionUrl")
  def username = column[Option[String]]("username")
  def password = column[Option[String]]("password")
  def timezone = column[Option[String]]("timezone")
  def id = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("jdbcdtatsource_id_fk", id, resources)(_.id)

  def * : ProvenShape[JIJdbcDatasource] = (driver, connectionUrl, username, password, timezone, id).mapTo[JIJdbcDatasource]
}


trait JIDataSourceSupport { myself: DBRepository =>

  def selectDataSourceModel(path: String): Future[DataSourceResource] = selectDataSourceModel(Left(path))

  def selectDataSourceModel(id: Long): Future[DataSourceResource] = selectDataSourceModel(Right(id))

  private def selectDataSourceModel(pathOrId: Either[String, Long]): Future[DataSourceResource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
        } yield (resource, folder)
      case Right(id) =>
        for {
          resource <- resources.filter(_.id === id)
          folder   <- resource.parentFolderFk
        } yield (resource, folder)
    }

    dbContext.run(action.result.head).flatMap{ case (resource, folder) =>
      val subQuery = resource.resourceType match {
        case DBResourceTypes.jdbcDataSource =>
          jdbcResources.filter(_.id === resource.id)
        case DBResourceTypes.jndiJdbcDataSource => ???
      }
      dbContext.run(subQuery.result.head).map {
        case jdbc: JIJdbcDatasource =>
          val fr = JdbcDataSourceResource(s"${folder.uri}/${resource.name}", resource.label)
          fr.version = resource.version
          fr.permissionMask = 1
          fr.timezone = jdbc.timezone
          fr.driverClass = Some(jdbc.driver)
          fr.username = jdbc.username
          fr.password = jdbc.password
          fr.connectionUrl = jdbc.connectionUrl
          fr
        case x =>
          logger.error(s"Unimplemented error: $x")
          ???
      }
    }
  }

  def selectJdbcDataSource(path: String): Future[JIJdbcDatasource] = selectJdbcDataSource(Left(path))

  def selectJdbcDataSource(id: Long): Future[JIJdbcDatasource] = selectJdbcDataSource(Right(id))

  private def selectJdbcDataSource(pathOrId: Either[String, Long]): Future[JIJdbcDatasource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
        } yield (resource, folder)
      case Right(id) =>
        for {
          resource <- resources.filter(_.id === id)
          folder   <- resource.parentFolderFk
        } yield (resource, folder)
    }

    dbContext.run(action.result.head).flatMap{ case (resource, folder) =>
      val subQuery = resource.resourceType match {
        case DBResourceTypes.jdbcDataSource =>
          jdbcResources.filter(_.id === resource.id)
        case DBResourceTypes.jndiJdbcDataSource => ???
      }
      dbContext.run(subQuery.result.head)
    }
  }

  def insertDataSource(request: DataSourceResource): Future[Long] = {
    request match {
      case req: JdbcDataSourceResource => insertDataSource(req)
    }
  }

  def insertDataSource(request: JdbcDataSourceResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.jdbcDataSource, version = request.version + 1))
      jdbcResourceId <- insertDataSource( JIJdbcDatasource(request.driverClass.get, request.connectionUrl, request.username, request.password, request.timezone, resourceId) )
    } yield jdbcResourceId
  }

  def insertDataSource(jdbc: JIJdbcDatasource): Future[Long] = {
    val action = jdbcResources += jdbc
    dbContext.run(action).map( _ => jdbc.id )
  }
}

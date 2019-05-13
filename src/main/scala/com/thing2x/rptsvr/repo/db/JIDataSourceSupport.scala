package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.repo.db.DBSchema._
import com.thing2x.rptsvr.{DataSourceResource, JdbcDataSourceResource}
import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape

import scala.concurrent.Future


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


final case class JIDataSourceModel(ds: AnyRef, resource: JIResource, uri: String) extends DBModelKind

trait JIDataSourceSupport { myself: DBRepository =>

  def asApiModel(model: JIDataSourceModel): DataSourceResource = {
    model.ds match {
      case jdbc: JIJdbcDatasource =>
        val fr = JdbcDataSourceResource(model.uri, model.resource.label)
        fr.version = model.resource.version
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

  def selectDataSourceResource(path: String): Future[JIDataSourceModel] = selectDataSourceResource(Left(path))

  def selectDataSourceResource(id: Long): Future[JIDataSourceModel] = selectDataSourceResource(Right(id))

  private def selectDataSourceResource(pathOrId: Either[String, Long]): Future[JIDataSourceModel] = {
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
      dbContext.run(subQuery.result.head).map{ ds =>
        JIDataSourceModel(ds, resource, s"${folder.uri}/${resource.name}")
      }
    }
  }

  def insertDataSourceResource(request: DataSourceResource): Future[Long] = {
    request match {
      case req: JdbcDataSourceResource => insertDataSourceResource(req)
    }
  }

  def insertDataSourceResource(request: JdbcDataSourceResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.jdbcDataSource, version = request.version + 1))
      jdbcResourceId <- insertDataSourceResource( JIJdbcDatasource(request.driverClass.get, request.connectionUrl, request.username, request.password, request.timezone, resourceId) )
    } yield jdbcResourceId
  }

  def insertDataSourceResource(jdbc: JIJdbcDatasource): Future[Long] = {
    val action = jdbcResources += jdbc
    dbContext.run(action).map( _ => jdbc.id )
  }
}

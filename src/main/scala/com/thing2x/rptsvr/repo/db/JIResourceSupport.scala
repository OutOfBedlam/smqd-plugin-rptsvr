package com.thing2x.rptsvr.repo.db
import java.sql.Date

import com.thing2x.rptsvr.Resource
import com.thing2x.rptsvr.repo.db.DBSchema._

import scala.concurrent.Future

import DBSchema.profile.api._

//     create table JIResource (
//        id number(19,0) not null,
//        version number(10,0) not null,
//        name nvarchar2(200) not null,
//        parent_folder number(19,0) not null,
//        childrenFolder number(19,0),
//        label nvarchar2(200) not null,
//        description nvarchar2(250),
//        resourceType nvarchar2(255) not null,
//        creation_date date not null,
//        update_date date not null,
//        primary key (id),
//        unique (name, parent_folder)
//    );
final case class JIResource( name: String,
                             parentFolder: Long,
                             childrenFolder: Option[Long],
                             label: String,
                             descriptoin: Option[String],
                             resourceType: String,
                             creationDate: Date = new Date(System.currentTimeMillis),
                             updateDate: Date = new Date(System.currentTimeMillis),
                             version: Int = -1,
                             id: Long = 0L)

final class JIResourceTable(tag: Tag) extends Table[JIResource](tag, "JIResource") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def version = column[Int]("version")
  def name = column[String]("name")
  def parentFolder = column[Long]("parent_folder")
  def childrenFolder = column[Option[Long]]("childrenFolder")
  def label = column[String]("label")
  def description = column[Option[String]]("description")
  def resourceType = column[String]("resourceType")
  def creationDate = column[Date]("creation_date")
  def updateDate = column[Date]("update_date")

  def parentFolderFk = foreignKey("resource_parent_folder_fk", parentFolder, resourceFolders)(_.id)

  def * = (name, parentFolder, childrenFolder, label, description, resourceType, creationDate, updateDate, version, id).mapTo[JIResource]
}


trait JIResourceSupport { mySelf: DBRepository =>

  def selectResourceModel(path: String): Future[Resource] = selectResourceModel(Left(path))

  def selectResourceModel(id: Long): Future[Resource] = selectResourceModel(Right(id))

  private def selectResourceModel(pathOrId: Either[String, Long]): Future[Resource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folderId <- resourceFolders.filter(_.uri === folderPath).map(_.id)
          resource <- resources.filter(_.parentFolder === folderId).filter(_.name === name)
        } yield resource
      case Right(id)  =>
        resources.filter(_.id === id)
    }
    dbContext.run(action.result.head).flatMap { r =>
      r.resourceType match {
        case DBResourceTypes.file =>              selectFileResourceModel(r.id)
        case DBResourceTypes.jdbcDataSource =>    selectDataSourceModel(r.id)
        case DBResourceTypes.query =>             selectQueryResourceModel(r.id)
        case DBResourceTypes.reportUnit =>        selectReportUnitModel(r.id)
        // TODO:
      }
    }
  }

  def selectResource(path: String): Future[JIResource] = selectResource(Left(path))

  def selectResource(id: Long): Future[JIResource] = selectResource(Right(id))

  private def selectResource(pathOrId: Either[String, Long]): Future[JIResource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folderId <- resourceFolders.filter(_.uri === folderPath).map(_.id)
          resource <- resources.filter(_.parentFolder === folderId).filter(_.name === name)
        } yield resource
      case Right(id)  =>
        resources.filter(_.id === id)
    }
    dbContext.run(action.result.head)
  }

  def insertResource(resource: JIResource): Future[Long] = {
    val act = resources returning resources.map(_.id) += resource
    dbContext.run(act)
  }
}

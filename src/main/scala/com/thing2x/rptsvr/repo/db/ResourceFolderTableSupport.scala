package com.thing2x.rptsvr.repo.db

import java.sql.Date

import com.thing2x.rptsvr.FolderResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

//    create table JIResourceFolder (
//        id number(19,0) not null,
//        version number(10,0) not null,
//        uri nvarchar2(250) not null,
//        hidden number(1,0),
//        name nvarchar2(200) not null,
//        label nvarchar2(200) not null,
//        description nvarchar2(250),
//        parent_folder number(19,0),
//        creation_date date not null,
//        update_date date not null,
//        primary key (id),
//        unique (uri)
//    );
final case class JIResourceFolder( uri: String,
                                   name: String,
                                   label: String,
                                   description: Option[String],
                                   parentFolder: Long,
                                   hidden: Boolean = false,
                                   creationDate: Date = new Date(System.currentTimeMillis),
                                   updateDate: Date = new Date(System.currentTimeMillis),
                                   version: Int = -1,
                                   id: Long = 0L) extends JIDataModelKind


final class JIResourceFolderTable(tag: Tag) extends Table[JIResourceFolder](tag, "JIResourceFolder") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def version = column[Int]("version")
  def uri = column[String]("uri", O.Unique)
  def hidden = column[Boolean]("hidden", O.Default(false))
  def name = column[String]("name")
  def label = column[String]("label")
  def description = column[Option[String]]("description")
  def parentFolder = column[Long]("parent_folder")
  def creationDate = column[Date]("creation_date")
  def updateDate = column[Date]("update_date")

  def * = (uri, name, label, description, parentFolder, hidden, creationDate, updateDate, version, id).mapTo[JIResourceFolder]
}


trait ResourceFolderTableSupport { mySelf: DBRepository =>

  def asApiModel(model: JIResourceFolder): FolderResource = {
    val fr = FolderResource(model.uri, model.label)
    fr.version = model.version
    fr.permissionMask = 1
    fr
  }

  def selectResourceFolder(path: String): Future[JIResourceFolder] = selectResourceFolder(Left(path))

  def selectResourceFolder(id: Long): Future[JIResourceFolder] =  selectResourceFolder(Right(id))

  private def selectResourceFolder(pathOrId: Either[String, Long]): Future[JIResourceFolder] = {
    val action = pathOrId match {
      case Left(path) => resourceFolders.filter(_.uri === path)
      case Right(id) => resourceFolders.filter(_.id === id)
    }
    dbContext.run(action.result.head)
  }

  def selectSubFoldersFromResourceFolder(path: String): Future[Seq[JIResourceFolder]] = {
    val action = for {
      folderId <- resourceFolders.filter(_.uri === path).map(_.id)
      childFolders <- resourceFolders.filter(_.parentFolder === folderId)
    } yield childFolders

    dbContext.run(action.result)
  }

  def selectResourcesFromResourceFolder(path: String): Future[Seq[JIResource]] = {
    val action = for {
      folderId <- resourceFolders.filter(_.uri === path).map(_.id)
      rscs     <- resources.filter(_.parentFolder === folderId)
    } yield rscs

    dbContext.run(action.result)
  }

  def insertResourceFolder(request: FolderResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map(_.id)
      id <- insertResourceFolder( JIResourceFolder(request.uri, name, request.label, request.description, parentFolderId, version = request.version + 1) )
    } yield id
  }

  def insertResourceFolder(folder: JIResourceFolder): Future[Long] = {
    val act = resourceFolders returning resourceFolders.map(_.id) += folder
    dbContext.run(act)
  }
}

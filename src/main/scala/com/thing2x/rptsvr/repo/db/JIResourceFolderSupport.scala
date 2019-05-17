// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.thing2x.rptsvr.repo.db

import java.sql.Date

import com.thing2x.rptsvr.FolderResource

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
                                   id: Long = 0L)

trait JIResourceFolderSupport { mySelf: DBRepository =>

  import dbContext.profile.api._

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


  def selectResourceFolderModel(path: String): Future[FolderResource] = selectResourceFolderModel(Left(path))

  def selectResourceFolderModel(id: Long): Future[FolderResource] = selectResourceFolderModel(Right(id))

  private def selectResourceFolderModel(pathOrId: Either[String, Long]): Future[FolderResource] = {
    val action = pathOrId match {
      case Left(path) =>
        resourceFolders.filter(_.uri === path)
      case Right(id) =>
        resourceFolders.filter(_.id === id)
    }
    dbContext.run(action.result.head).map{ r =>
      val fr = FolderResource(r.uri, r.label)
      fr.version = r.version
      fr.permissionMask = 1
      fr
    }
  }

  def selectResourceFolder(path: String): Future[JIResourceFolder] = selectResourceFolder(Left(path))

  def selectResourceFolder(id: Long): Future[JIResourceFolder] = selectResourceFolder(Right(id))

  private def selectResourceFolder(pathOrId: Either[String, Long]): Future[JIResourceFolder] = {
    val action = pathOrId match {
      case Left(path) => resourceFolders.filter(_.uri === path)
      case Right(id) => resourceFolders.filter(_.id === id)
    }
    dbContext.run(action.result.head)
  }

  def selectSubFolderModelFromResourceFolder(path: String): Future[Seq[FolderResource]] = {
    val action = for {
      folderId <- resourceFolders.filter(_.uri === path).map(_.id)
      childFolders <- resourceFolders.filter(_.parentFolder === folderId)
    } yield childFolders

    dbContext.run(action.result).map(rs => rs.map{ r =>
      val fr = FolderResource(r.uri, r.label)
      fr.version = r.version
      fr.permissionMask = 1
      fr
    })
  }

  def selectResourcesFromResourceFolder(path: String): Future[Seq[JIResource]] = {
    val action = for {
      folderId <- resourceFolders.filter(_.uri === path).map(_.id)
      rscs     <- resources.filter(_.parentFolder === folderId)
    } yield rscs

    dbContext.run(action.result)
  }

  def selectResourceFolderModelIfExists(path: String): Future[Option[FolderResource]] = {
    val action = resourceFolders.filter(_.uri === path)
    dbContext.run(action.result.headOption).map{
      case Some(r) =>
        val fr = FolderResource(r.uri, r.label)
        fr.version = r.version
        fr.permissionMask = 1
        Some(fr)
      case None => None
    }
  }

  def existsResourceFolder(uri: String): Future[Boolean] = {
    val action = for {
      f <- resourceFolders.filter(_.uri === uri)
    } yield f

    dbContext.run(action.exists.result)
  }

  def insertResourceFolderIfNotExists(uri: String): Future[Long] = {
    val (parentPath, name) = splitPath(uri)
    existsResourceFolder( uri ).flatMap { exists =>
      if (exists) {
        selectResourceFolder( uri ).map( _.id )
      }
      else {
        for {
          parentFolderId <- insertResourceFolderIfNotExists( parentPath ) if !exists
          folderId       <- insertResourceFolder( JIResourceFolder(uri, name, name, None, parentFolderId) ) if !exists
          folder         <- selectResourceFolder(folderId)
        } yield folder.id
      }
    }
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

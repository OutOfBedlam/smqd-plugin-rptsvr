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

import com.thing2x.rptsvr.Resource

import scala.concurrent.Future

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

trait JIResourceSupport { mySelf: DBRepository =>

  import dbContext.profile.api._

  final class JIResourceTable(tag: Tag) extends Table[JIResource](tag, "JIRESOURCE") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def version = column[Int]("VERSION")
    def name = column[String]("NAME")
    def parentFolder = column[Long]("PARENT_FOLDER")
    def childrenFolder = column[Option[Long]]("CHILDRENFOLDER")
    def label = column[String]("LABEL")
    def description = column[Option[String]]("DESCRIPTION")
    def resourceType = column[String]("RESOURCETYPE")
    def creationDate = column[Date]("CREATION_DATE")
    def updateDate = column[Date]("UPDATE_DATE")

    def parentFolderFk = foreignKey("RESOURCE_PARENT_FOLDER_FK", parentFolder, resourceFolders)(_.id)

    def * = (name, parentFolder, childrenFolder, label, description, resourceType, creationDate, updateDate, version, id).mapTo[JIResource]
  }

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

  def insertOrUpdateResource(resource: JIResource): Future[Long] = {
    val act = resources.insertOrUpdate(resource)
    dbContext.run(act).map(_ => resource.id)
  }
}

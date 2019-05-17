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

import java.util.Base64

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.thing2x.rptsvr.api.MimeTypes
import com.thing2x.rptsvr.{FileContent, FileResource}
import slick.lifted.ProvenShape

import scala.concurrent.Future

//    create table JIFileResource (
//        id number(19,0) not null,
//        data blob,
//        file_type nvarchar2(20),
//        reference number(19,0),
//        primary key (id)
//    );
final case class JIFileResource( fileType: String,
                                 data: Option[Array[Byte]],
                                 reference: Option[Long],
                                 id: Long = 0L)

trait JIFileResourceSupport { mySelf: DBRepository =>
  import dbContext.profile.api._

  final class JIFileResourceTable(tag: Tag) extends Table[JIFileResource](tag, "JIFileResource") {
    def fileType    = column[String]("file_type")
    def data   = column[Array[Byte]]("data", O.SqlType("BLOB"))
    def reference    = column[Long]("reference")
    def id           = column[Long]("id", O.PrimaryKey)

    def idFk = foreignKey("fileresource_id_fk", id, resources)(_.id)

    def * : ProvenShape[JIFileResource] = (fileType, data.?, reference.?, id) <> (JIFileResource.tupled, JIFileResource.unapply)
  }

  def selectFileResourceModelList(ids: Seq[Long]): Future[Seq[FileResource]] = {
    Future.sequence( ids.map( id => selectFileResourceModel(id)) )
  }

  def selectFileContentModel(path: String): Future[FileContent] = selectFileContentModel(Left(path))

  def selectFileContentModel(id: Long): Future[FileContent] = selectFileContentModel(Right(id))

  private def selectFileContentModel(pathOrId: Either[String, Long]): Future[FileContent] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          fileResource <- fileResources.filter(_.id === resource.id)
        } yield (folder, resource, fileResource)

      case Right(id)  =>
        for {
          fileResource <- fileResources.filter(_.id === id)
          resource     <- fileResource.idFk
          folder       <- resource.parentFolderFk
        } yield (folder, resource, fileResource)
    }

    dbContext.run(action.result.head).map{ case (folder, resource, fileResource) =>
      val src = fileResource.data.map( d => Source.fromFuture(Future( ByteString(d) )) ).getOrElse(Source.empty)
      FileContent( s"${folder.uri}/${resource.name}", src, MimeTypes.mimeTypeOf( fileResource.fileType, resource.name) )
    }
  }

  def selectFileResourceModel(path: String): Future[FileResource] = selectFileResourceModel(Left(path))

  def selectFileResourceModel(id: Long): Future[FileResource] = selectFileResourceModel(Right(id))

  private def selectFileResourceModel(pathOrId: Either[String, Long]): Future[FileResource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          fileResource <- fileResources.filter(_.id === resource.id)
        } yield (folder, resource, fileResource)

      case Right(id)  =>
        for {
          fileResource <- fileResources.filter(_.id === id)
          resource     <- fileResource.idFk
          folder       <- resource.parentFolderFk
        } yield (folder, resource, fileResource)
    }

    dbContext.run(action.result.head).map{ case(folder, resource, file) =>
      val fr = FileResource(s"${folder.uri}/${resource.name}", resource.label)
      fr.version = resource.version
      fr.permissionMask = 1
      fr.fileType = file.fileType
      fr
    }
  }

  def selectFileResource(path: String): Future[JIFileResource] = selectFileResource(Left(path))

  def selectFileResource(id: Long): Future[JIFileResource] = selectFileResource(Right(id))

  private def selectFileResource(pathOrId: Either[String, Long]): Future[JIFileResource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          fileResource <- fileResources.filter(_.id === resource.id)
        } yield fileResource
      case Right(id) =>
        for {
          fileResource <- fileResources.filter(_.id === id)
        } yield fileResource
    }
    dbContext.run(action.result.head)
  }

  def insertFileResource(request: FileResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)

    for {
      parentFolderId <- insertResourceFolderIfNotExists(parentFolderPath)
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.file, version = request.version + 1) )
      _              <- insertFileResource( JIFileResource(request.fileType, request.content.map{ ctnt => Base64.getDecoder.decode(ctnt) }, None, resourceId) )
    } yield resourceId
  }

  def insertFileResource(file: JIFileResource): Future[Long] = {
    val action = fileResources += file
    dbContext.run(action).map( _ => file.id )
  }

  def insertOrUpdateFileResource(file: JIFileResource): Future[Long] = {
    val action = fileResources.insertOrUpdate(file)
    dbContext.run(action).map( _ => file.id )
  }
}

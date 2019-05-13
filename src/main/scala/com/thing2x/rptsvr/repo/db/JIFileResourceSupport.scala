package com.thing2x.rptsvr.repo.db

import java.util.Base64

import com.thing2x.rptsvr.{FileResource, RepositoryContext}
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._
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

final class JIFileResourceTable(tag: Tag) extends Table[JIFileResource](tag, "JIFileResource") {
  def fileType    = column[String]("file_type")
  def data   = column[Option[Array[Byte]]]("data", O.SqlType("BLOB"))
  def reference    = column[Option[Long]]("reference")
  def id           = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("fileresource_id_fk", id, resources)(_.id)

  def * : ProvenShape[JIFileResource] = (fileType, data, reference, id).mapTo[JIFileResource]
}

final case class JIFileResourceModel(file: JIFileResource, resource: JIResource, uri: String) extends DBModelKind

trait JIFileResourceSupport { mySelf: DBRepository =>

  def asApiModel(model: JIFileResourceModel): FileResource = {
    val fr = FileResource(model.uri, model.resource.label)
    fr.version = model.resource.version
    fr.permissionMask = 1
    fr.fileType = model.file.fileType
    fr
  }

  def selectFileResource(path: String): Future[JIFileResourceModel] = selectFileResource(Left(path))

  def selectFileResource(id: Long): Future[JIFileResourceModel] = selectFileResource(Right(id))

  private def selectFileResource(pathOrId: Either[String, Long]): Future[JIFileResourceModel] = {
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
      JIFileResourceModel(file, resource, s"${folder.uri}/${resource.name}")
    }
  }

  def insertFileResource(request: FileResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)

    for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.file, version = request.version + 1) )
      _              <- insertFileResource( JIFileResource(request.fileType, request.content.map{ ctnt => Base64.getDecoder.decode(ctnt) }, None, resourceId) )
    } yield resourceId
  }

  def insertFileResource(file: JIFileResource): Future[Long] = {
    val action = fileResources += file
    dbContext.run(action).map( _ => file.id )
  }

  //def selectFileContent(path: String):
}

package com.thing2x.rptsvr.repo.db

import java.util.Base64

import com.thing2x.rptsvr.FileResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait FileResourceTableSupport { mySelf: DBRepository =>

  def selectFileResource(path: String): Future[JIResourceObject] = selectFileResource(Left(path))

  def selectFileResource(id: Long): Future[JIResourceObject] = selectFileResource(Right(id))

  private def selectFileResource(pathOrId: Either[String, Long]): Future[JIResourceObject] = {
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

    dbContext.run(action.result.head).map( JIResourceObject(_) )
  }

  def insertFileResource(request: FileResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)

    for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, JIResourceTypes.file, version = request.version + 1) )
      _              <- insertFileResource( JIFileResource(request.fileType, request.content.map{ ctnt => Base64.getDecoder.decode(ctnt) }, None, resourceId) )
    } yield resourceId
  }

  def insertFileResource(file: JIFileResource): Future[Long] = {
    val action = fileResources += file
    dbContext.run(action).map( _ => file.id )
  }

  //def selectFileContent(path: String):
}

package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.FolderResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait ResourceFolderTableSupport { mySelf: DBRepository =>

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

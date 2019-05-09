package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.FolderResource
import com.thing2x.rptsvr.repo.db.DBRepository.resourceFolders
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait ResourceFolderTableSupport { mySelf: DBRepository =>

  def selectFolder(path: String): Future[Option[FolderResource]] = selectFolder(Left(path))

  def selectFolder(id: Long): Future[Option[FolderResource]] =  selectFolder(Right(id))

  def selectFolder(pathOrId: Either[String, Long]): Future[Option[FolderResource]] = {
    val action = pathOrId match {
      case Left(path) => resourceFolders.filter(_.uri === path)
      case Right(id) => resourceFolders.filter(_.id === id)
    }

    logger.trace(s"selectFolder $pathOrId ${action.result.statements.mkString}")

    dbContext.run(action.result.headOption).map {
      case Some(r) =>
        val fr = FolderResource(r.uri, r.label)
        fr.version = r.version
        fr.permissionMask = 1
        Some(fr)
      case _ =>
        None
    }
  }

  def selectFolderId(path: String): Future[Option[Long]] = {
    val idQuery = resourceFolders.filter( _.uri === path ).map( _.id )
    dbContext.run(idQuery.result.headOption)
  }

  def insertFolder(request: FolderResource): Future[Option[Long]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    selectFolderId(parentFolderPath).flatMap {
      case Some(parentFolderId) =>
        insertFolder( JIResourceFolder(request.uri, name, request.label, request.description, parentFolderId, version = request.version + 1) )
          .map( Some(_) )
      case _ => Future( None )
    }
  }

  def insertFolder(folder: JIResourceFolder): Future[Long] = {
    val act = resourceFolders returning resourceFolders.map(_.id) += folder
    dbContext.run(act)
  }

  def insertFolderThenSelect(request: FolderResource): Future[Option[FolderResource]] = {
    insertFolder(request).flatMap {
      case Some(folderId) => selectFolder( folderId )
      case _ => Future( None )
    }
  }

}

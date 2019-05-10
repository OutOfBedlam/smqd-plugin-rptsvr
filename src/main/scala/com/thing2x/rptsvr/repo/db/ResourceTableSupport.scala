package com.thing2x.rptsvr.repo.db
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait ResourceTableSupport { mySelf: DBRepository =>

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

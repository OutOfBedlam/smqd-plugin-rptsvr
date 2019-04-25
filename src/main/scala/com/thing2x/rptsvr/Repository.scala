package com.thing2x.rptsvr

import scala.concurrent.Future

trait Repository {
  def createFolder(path: String, createFolders: Boolean): Future[FolderResource]
  def getFolder(path: String): Future[FolderResource]
  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[Seq[Resource]]

  def getResource(path: String, expanded: Boolean): Future[Resource]

  def deleteResource(path: String): Future[Boolean]
}

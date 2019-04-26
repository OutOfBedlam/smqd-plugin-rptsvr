package com.thing2x.rptsvr

import scala.concurrent.Future

trait Repository {
  def createFolder(request: CreateFolderRequest): Future[FolderResource]
  def getFolder(path: String): Future[FolderResource]
  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[Seq[Resource]]

  def createFile(request: CreateFileRequest, createFolders: Boolean, overwrite: Boolean): Future[FileResource]
  def getResource(path: String, expanded: Boolean): Future[Resource]

  def deleteResource(path: String): Future[Boolean]
}

class RepositoryException extends Exception

class ResourceAlreadyExistsExeption(uri: String) extends Exception
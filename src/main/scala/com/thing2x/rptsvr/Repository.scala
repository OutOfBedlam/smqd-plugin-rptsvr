package com.thing2x.rptsvr

import scala.concurrent.Future

trait Repository {
  def createFolder(request: CreateFolderRequest): Future[Result[FolderResource]]
  def getFolder(path: String): Future[Result[FolderResource]]
  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]]

  def createFile(request: CreateFileRequest, createFolders: Boolean, overwrite: Boolean): Future[Result[FileResource]]
  def getResource(path: String, expanded: Boolean): Future[Result[Resource]]

  def deleteResource(path: String): Future[Boolean]
}

class RepositoryException extends Exception

class ResourceNotFoundException(uri: String) extends Exception
class ResourceAlreadyExistsExeption(uri: String) extends Exception
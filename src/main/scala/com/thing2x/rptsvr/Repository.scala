package com.thing2x.rptsvr

import java.io.File

import akka.http.scaladsl.model.ContentType
import com.thing2x.rptsvr.Repository.ResourceContentResponse
import com.thing2x.smqd.Smqd
import com.typesafe.config.Config

import scala.concurrent.Future

object Repository {
  def findInstance(smqd: Smqd): Repository = {
    val repositoryClass = classOf[Repository]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      repositoryClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[Repository]).get
  }

  case class CreateFolderRequest(uri: String, label: String, permissionMask: Int, version: Int)

  case class CreateFileRequest(uri: String, label: String, permissionMask: Option[Int], version: Int, `type`: String,
                               content: String, description: Option[String],
                               creationDate: Option[String], updateDate: Option[String])

  case class ResourceLookupResponse(resourceLookup: Seq[Resource])

  case class ResourceContentResponse(uri: String, file: File, contentType: ContentType)
}

trait Repository {
  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]]

  def setResource(path: String, request: Config, createFolders: Boolean, overwrite: Boolean, resourceType: String): Future[Result[Resource]]
  def getResource(path: String): Future[Result[Resource]]

  // TODO: this method should be return Source (or InputStream) instead of File : this was intended for the quick develop purpose
  def getContent(path: String): Future[ResourceContentResponse]

  def deleteResource(path: String): Future[Boolean]
}

class RepositoryException extends Exception

class ResourceNotFoundException(uri: String) extends Exception
class ResourceAlreadyExistsExeption(uri: String) extends Exception
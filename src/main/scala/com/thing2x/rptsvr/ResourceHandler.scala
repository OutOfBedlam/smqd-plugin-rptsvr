package com.thing2x.rptsvr

import com.thing2x.rptsvr.ResourceHandler.DSJdbcResource
import com.thing2x.rptsvr.repo.fs.FileRepository
import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

object ResourceHandler {
  case class FolderResource(uri: String,
                            label: String,
                            description: String,
                            permissionMask: Int,
                            creationDate: String,
                            updateDate: String,
                            version: Int,
                            resourceType: String = "folder"
                           ) extends Resource

  case class ReportUnitResource(uri: String,
                                label: String,
                                permissionMask: Int,
                                creationDate: String,
                                updateDate: String,
                                version: Int,
                                resourceType: String = "reportUnit"
                               ) extends Resource

  case class DSJdbcResource(uri: String,
                            label: String,
                            permissionMask: Int,
                            driverClass: String,
                            connectionUrl: String,
                            username: String,
                            password: String,
                            version: Int
                           ) extends Resource

  case class FileResource(uri: String,
                           label: String,
                           permissionMask: Int,
                           creationDate: String,
                           updateDate: String,
                           version: Int,
                           `type`: String) extends Resource
}

class ResourceHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends StrictLogging {

  private val repo = smqd.pluginManager.pluginDefinitions.find(pd => pd.clazz == classOf[FileRepository]).map(_.instances.head.instance.asInstanceOf[Repository]).get

  def getResource(path: String, expanded: Boolean): Future[Resource] =
    repo.getResource(path, expanded)

  def listFolder(folderUri: String, recursive: Boolean, sortBy: String, limit: Int): Future[Seq[Resource]] =
    repo.listFolder(folderUri, recursive, sortBy, limit)

  def storeResource(path: String, ds: DSJdbcResource, createFolders: Boolean, overwrite: Boolean): Future[DSJdbcResource] = Future {
    logger.debug(s"path=$path createFolders=$createFolders overwrite=$overwrite $ds")
    //repo.store(path)
    ds
  }
}

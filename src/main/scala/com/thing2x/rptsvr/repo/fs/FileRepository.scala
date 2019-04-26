package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat
import java.util.{Base64, Date}

import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.language.implicitConversions

class FileRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging {

  import smqd.Implicit.gloablDispatcher

  private implicit val context: FileRepositoryContext = {
    val root = new File(config.getString("basedir"))

    if (!root.canRead || !root.canWrite || !root.isDirectory) {
      throw new IllegalAccessError(s"can not access basedir: ${root.getPath}")
    }

    val dateFormat = new SimpleDateFormat(config.getString("formats.date"))
    val datetimeFormat = new SimpleDateFormat(config.getString("formats.datetime"))
    new FileRepositoryContext(root, dateFormat, datetimeFormat)
  }

  override def start(): Unit = {
    FrFolder("/").meta
  }

  override def stop(): Unit = {
  }

  /////////////////////////////////////////////////
  // Folder
  /////////////////////////////////////////////////

  def createFolder(request: CreateFolderRequest): Future[FolderResource] = Future {
    logger.debug(s"create folder: $request")
    val fr = FrFolder(request.uri)
    fr.mkdir(request.label, request.permissionMask, request.version + 1)
    fr.asResource
  }

  def getFolder(uri: String): Future[FolderResource] = Future {
    FrFolder(uri).asResource
  }

  def listFolder(uri: String, recursive: Boolean, sortBy: String, limit: Int): Future[Seq[Resource]] = Future {
    logger.debug(s"list folder: $uri")
    FrFolder(uri).list.map(_.asResource)
  }

  /////////////////////////////////////////////////
  // File
  /////////////////////////////////////////////////

  override def createFile(request: CreateFileRequest, createFolders: Boolean, overwrite: Boolean): Future[FileResource] = Future {
    logger.debug(s"create file: $request")
    // uri: String, label: String, permissionMask: Int, version: Int, `type`: String, content: String
    val fr = FrFile(request.uri, request.`type`)

    if (!fr.exists || overwrite ) {
      val ctnt = Base64.getDecoder.decode(request.content)
      fr.write(ctnt, Map(META_LABEL(request.label), META_PERMISSIONMASK(request.permissionMask), META_VERSION(request.version)))
    }

    fr.asResource.asInstanceOf[FileResource]
  }

  /////////////////////////////////////////////////
  // General Resource
  /////////////////////////////////////////////////

  def getResource(path: String, expanded: Boolean): Future[Resource] = Future {
    logger.debug(s"get resource: $path expanded=$expanded")
    val fr = FrFile(path)
    fr.asResource
  }

  def deleteResource(path: String): Future[Boolean] = Future {
    logger.debug(s"delete resource: $path")

    val file = FrFile(path)
    file.delete()
  }
}

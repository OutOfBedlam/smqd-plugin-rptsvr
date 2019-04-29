package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.language.implicitConversions

class FileRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging {

  import smqd.Implicit.gloablDispatcher

  private implicit val fsContext: FileRepositoryContext = {
    val root = new File(config.getString("basedir"))

    if (!root.canRead || !root.canWrite || !root.isDirectory) {
      throw new IllegalAccessError(s"can not access basedir: ${root.getPath}")
    }

    val dateFormat = new SimpleDateFormat(config.getString("formats.date"))
    val datetimeFormat = new SimpleDateFormat(config.getString("formats.datetime"))
    new FileRepositoryContext(this, smqd.Implicit.gloablDispatcher, root, dateFormat, datetimeFormat)
  }

  override val context: RepositoryContext = fsContext

  override def start(): Unit = {
    FsFile("/").mkdir("Root")
    FsFile("/public").mkdir("Public")
  }

  override def stop(): Unit = {
  }

  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]] = Future {
    logger.debug(s"list folder: $path")
    val fr = FsFile(path)
    if (fr.exists)
      Right(fr.list.map(_.asResource))
    else
      Left(new ResourceNotFoundException(path))
  }

  def setResource(path: String, resource: Resource, createFolders: Boolean, overwrite: Boolean): Future[Result[Resource]] = Future {
    logger.debug(s"set resource uri=${resource.uri}")

    val fr = FsFile(path)
    if ( !fr.exists || overwrite ) {
      fr.metaFile.getParentFile.mkdirs()
      if (resource.creationDate.isEmpty) resource.creationDate = new Date(System.currentTimeMillis)
      if (resource.updateDate.isEmpty) resource.updateDate = new Date(System.currentTimeMillis)
      // increase versiopn number
      resource.version += 1
      resource.write(fr)
    }

    fr.asResource
  }

  def getResource(path: String): Future[Result[Resource]] = Future {
    try {
      val fr = FsFile(path)
      if (fr.exists)
        Right(fr.asResource)
      else
        Left(new ResourceNotFoundException(path))
    }
    catch {
      case ex: Throwable => Left(ex)
    }
  }

  def getContent(path: String): Future[FileContent] = Future {
    val fr = FsFile(path)

    logger.trace(s"------------> getContent path=$path resourceType=${fr.resourceType} contentType=${fr.contentType}")
    FileContent(fr.uri, fr.contentFile, fr.contentType)
  }

  def deleteResource(path: String): Future[Boolean] = Future {
    logger.debug(s"delete resource: $path")

    val file = FsFile(path)
    file.delete()
  }

  def writeResource(resource: Resource): Unit = {

  }
}

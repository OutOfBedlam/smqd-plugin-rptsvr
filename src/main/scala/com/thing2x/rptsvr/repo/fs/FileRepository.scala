package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat

import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.thing2x.smqd.util.ConfigUtil._
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

  def setResource(path: String, request: Config, createFolders: Boolean, overwrite: Boolean, resourceType: String): Future[Result[Resource]] = Future {
    logger.debug(s"set resource: ${request.getOptionString(META_URI).getOrElse("<null>")}")

    val fr = FsFile(path)
    if ( !fr.exists || overwrite ) {
      fr.write(resourceType, request)
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
}

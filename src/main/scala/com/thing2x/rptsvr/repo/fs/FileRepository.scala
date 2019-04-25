package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import com.thing2x.rptsvr.{FileResource, FolderResource, ReportUnitResource, Repository, Resource}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.language.implicitConversions

object FileRepository {
}

class FileRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging {

  import smqd.Implicit.gloablDispatcher

  private val root = new File(config.getString("basedir"))

  if (!root.canRead || !root.canWrite || !root.isDirectory) {
    throw new IllegalAccessError(s"can not access basedir: ${root.getPath}")
  }

  private val rootPath = root.getCanonicalPath

  private val rootResource = FolderResource(uri="/", label="Organizations", description="Organizations",
    permissionMask=1, creationDate="2013-07-04T12:18:47", updateDate="2013-07-04T12:18:47", version=0)

  private val dateFormat = new SimpleDateFormat(config.getString("formats.date"))
  private val datetimeFormat = new SimpleDateFormat(config.getString("formats.datetime"))

  override def start(): Unit = {
    createFolder("/temp")
    createFolder("/public")
  }

  override def stop(): Unit = {

  }

  private def createFolder(folderUri: String): Future[FolderResource] = Future {
    val file = new File(root, folderUri)
    if (!file.exists)
      file.mkdirs()
    folderResource(file)
  }

  private def uri(file: File): String = {
    val l = file.getCanonicalPath.substring(rootPath.length)
    if (l.isEmpty) "/" else l
  }

  private def label(file: File): String = {
    val n = file.getName
    if (n.length == 1) {
      n.toUpperCase
    }
    else if (n.length > 1) {
      n.substring(0, 1).toUpperCase + n.substring(1)
    }
    else {
      n
    }
  }

  private def folderResource(file: File): FolderResource = {
    val lastModified = datetimeFormat.format(new Date(file.lastModified))
    val name = label(file)
    logger.debug(s"   Folder ==> ${uri(file)} ${label(file)}")
    FolderResource(uri(file), name, description=s"$name folder",
      permissionMask=1, creationDate=lastModified, updateDate=lastModified, version = 0)
  }

  private def fileResource(file: File, typ: String): FileResource = {
    val lastModified = datetimeFormat.format(new Date(file.lastModified))
    logger.debug(s"   File ($typ) ==> ${uri(file)} ${label(file)}")
    FileResource(uri(file), label(file), permissionMask=1, creationDate=lastModified, updateDate=lastModified, version = 0, typ)
  }

  private def reportUnitResource(file: File, expanded: Boolean): ReportUnitResource = {
    val lastModified = datetimeFormat.format(new Date(file.lastModified))
    val name = label(file)//file.getName.replaceFirst("[.]jrxml$", "")
    logger.debug(s"   Report Unit ==> ${uri(file)} $name")
    ReportUnitResource(uri(file), name, permissionMask = 1, creationDate = lastModified, updateDate = lastModified, version = 0)
  }

  def createFolder(path: String, createFolders: Boolean): Future[FolderResource] = Future {
    logger.debug(s"create folder: $path")
    val file = new File(root, path)
    val result = if (createFolders) file.mkdirs() else file.mkdir()
    if (result)
      getResource(path, expanded = false).asInstanceOf[Future[FolderResource]]
    else
      throw new RuntimeException(s"Fail to create folder: $path")
  }.flatten

  def getFolder(path: String): Future[FolderResource] = Future {
    val file = new File(root, path)
    folderResource(file)
  }

  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[Seq[Resource]] = Future {
    logger.debug(s"list folder: $path")

    val file = new File(root, path)
    file.listFiles().flatMap{ f =>
      if (f.isDirectory) {
        val meta = new File(f, "reportunit.json")
        if (meta.exists) {
          Some(reportUnitResource(f, expanded = false))
        }
        else {
          Some(folderResource(f))
        }
      }
      else if (f.isFile && f.getName.endsWith(".jrxml")) {
        Some(fileResource(f, "jrxml"))
      }
      else if (f.isFile && (f.getName.endsWith(".png") || f.getName.endsWith(".jpg") || f.getName.endsWith(".gif"))) {
        Some(fileResource(f, "img"))
      }
      else {
        None
      }
    }
  }

  def getResource(path: String, expanded: Boolean): Future[Resource] = Future {
    logger.debug(s"get resource: $path expanded=$expanded")
    path match {
      case "/" => rootResource
      case _ =>
        val file = new File(root, path)
        if (file.isDirectory) {
          val meta = new File(file, "reportunit.json")
          if (meta.exists) {
            reportUnitResource(file, expanded)
          }
          else {
            folderResource(file)
          }
        }
        else if (file.isFile && file.getName.endsWith(".jrxml")) {
          fileResource(file, "jrxml")
        }
        else if (file.isFile && (file.getName.endsWith(".png") || file.getName.endsWith(".jpg") || file.getName.endsWith(".gif"))) {
          fileResource(file, "img")
        }
        else {
          throw new RuntimeException(s"Resource not found: $path")
        }
    }
  }

  def deleteResource(path: String): Future[Boolean] = Future {
    logger.debug(s"delete resource: $path")
    val file = new File(root, path)
    file.delete()
  }
}

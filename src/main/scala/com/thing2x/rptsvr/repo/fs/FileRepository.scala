// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import akka.stream.scaladsl.FileIO
import com.thing2x.rptsvr.Repository.ResourceNotFoundException
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
    new FileRepositoryContext(this, smqd.Implicit.gloablDispatcher, smqd.Implicit.materializer, root, dateFormat, datetimeFormat)
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
      Right(fr.list.map(_.asResource()))
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

    fr.asResource()
  }

  def getResource(path: String, isReferenced: Boolean): Future[Result[Resource]] = Future {
    try {
      val fr = FsFile(path)
      if (fr.exists)
        Right(fr.asResource(isReferenced))
      else
        Left(new ResourceNotFoundException(path))
    }
    catch {
      case ex: Throwable => Left(ex)
    }
  }

  def getContent(path: String): Future[Either[Throwable, FileContent]] = Future {
    try {
      val fr = FsFile(path)
      logger.trace(s"get content path=$path resourceType=${fr.resourceType} contentType=${fr.contentType}")
      Right(FileContent(fr.uri, FileIO.fromPath(fr.contentFile.toPath), fr.contentType))
    }
    catch {
      case ex: Throwable => Left(ex)
    }
  }

  def deleteResource(path: String): Future[Boolean] = Future {
    logger.debug(s"delete resource: $path")

    val file = FsFile(path)
    file.delete()
  }
}

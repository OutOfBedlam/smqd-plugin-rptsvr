package com.thing2x.rptsvr.repo.db

import java.util.Base64

import com.thing2x.rptsvr.FileResource
import com.thing2x.rptsvr.repo.db.DBRepository.{fileResources, resourceFolders, resourceInsert}
import javax.sql.rowset.serial.SerialBlob
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait FileResourceTableSupport { mySelf: DBRepository =>
  def selectFile(meta: ResourceMeta): Future[Option[FileResource]] = {
    meta.resourceType match {
      case JIResourceTypes.file =>
        val subact = fileResources.filter(_.id === meta.id)
        dbContext.run(subact.result.head).map{ r =>
          val fr = FileResource(meta.uri, meta.label)
          fr.version = meta.version
          fr.permissionMask = 1
          fr.fileType = r.fileType
          Some(fr)
        }
      case _ =>
        Future( None )
    }
  }

  def insertFile(request: FileResource): Future[Option[FileResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val action = for {
      parentFolder <- resourceFolders.filter(_.uri === parentFolderPath).result.head
      fileId       <- resourceInsert += JIResource(name, parentFolder.id, None, request.label, request.description, JIResourceTypes.file, version = request.version + 1)
      _            <- fileResources += JIFileResource(request.fileType, request.content.map{ ctnt => new SerialBlob(Base64.getDecoder.decode(ctnt)) }, None, fileId)
    } yield fileId
    dbContext.run(action).flatMap { r =>
      selectResource(r, isReferenced = true) map {
        case Some(file) => Some(file.asInstanceOf[FileResource])
        case _ => None
      }
    }
  }

}

package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.util.Date

import com.thing2x.rptsvr.FolderResource
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.ConfigFactory

object FrFolder {
  def apply(uri: String)(implicit context: FileRepositoryContext): FrFolder = new FrFolder(new File(context.root, uri))
  def apply(file: File)(implicit context: FileRepositoryContext): FrFolder = new FrFolder(file)
}

class FrFolder(file: File)(implicit context: FileRepositoryContext) extends FrFile(file, "folder") {
  type R = FolderResource

  override val fileType: String = "folder"

  override def asResource: FolderResource =
    FolderResource(
      meta.getString(META_URI),
      meta.getString(META_LABEL),
      meta.getInt(META_PERMISSIONMASK),
      meta.getOptionString(META_DESCRIPTION).getOrElse(meta.getString(META_LABEL)),
      context.datetimeFormat.format(new Date(meta.getLong(META_CREATIONTIME))),
      context.datetimeFormat.format(new Date(meta.getLong(META_UPDATETIME))),
      meta.getInt(META_VERSION),
    )

  override def write(content: Array[Byte], meta: Map[String, Any]): Unit = ???

  def mkdir(label: String, permissionMask: Int, version: Int): Boolean = {
    if (file.mkdirs()) {
      writeMeta0(Map(
        META_TYPE("folder"),
        META_LABEL(label),
        META_VERSION(version),
        META_PERMISSIONMASK(permissionMask),
      ))
      true
    }
    else {
      false
    }
  }

  def list: Seq[FrFile] = {
    file.listFiles().flatMap{ child =>
      val childMetaFile = new File(child, METAFILENAME)

      if (child.isDirectory && childMetaFile.exists()) {
        val cfg = ConfigFactory.parseFile(childMetaFile)
        cfg.getString("type") match {
          case "folder" => Some(FrFolder(child))
          case "jrxml" => Some(FrJrxmlFile(child))
          case _ => None
        }
      }
      else {
        None
      }
    }
  }
}

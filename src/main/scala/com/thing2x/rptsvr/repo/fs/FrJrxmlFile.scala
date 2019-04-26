package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.util.Date

import com.thing2x.rptsvr.FileResource

object FrJrxmlFile {
  def apply(uri: String)(implicit context: FileRepositoryContext) = new FrJrxmlFile(new File(context.root, uri))
  def apply(file: File)(implicit context: FileRepositoryContext) = new FrJrxmlFile(file)
}

class FrJrxmlFile(file: File)(implicit context: FileRepositoryContext) extends FrFile(file, "jrxml") {
  override type R = FileResource

  override def asResource: FileResource =
    FileResource(
      meta.getString(META_URI),
      meta.getString(META_LABEL),
      meta.getInt(META_PERMISSIONMASK),
      context.datetimeFormat.format(new Date(meta.getLong(META_CREATIONTIME))),
      context.datetimeFormat.format(new Date(meta.getLong(META_UPDATETIME))),
      meta.getInt(META_VERSION),
      "jrxml"
    )

  override def write(content: Array[Byte], meta: Map[String, Any]): Unit = {
    file.mkdirs()
    writeMeta0(meta)
    writeContent0(content)
  }
}

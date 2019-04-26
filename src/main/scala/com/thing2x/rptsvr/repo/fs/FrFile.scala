package com.thing2x.rptsvr.repo.fs

import java.io.{File, FileNotFoundException, FileOutputStream, OutputStreamWriter}

import akka.util.ByteString
import com.thing2x.rptsvr.Resource
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.language.implicitConversions

object FrFile {

  def apply(uri: String)(implicit context: FileRepositoryContext): FrFile = {
    val file = new File(context.root, uri)
    val metaFile = new File(file, METAFILENAME)
    if (metaFile.exists) {
      val cfg = ConfigFactory.parseFile(metaFile)
      val typ = cfg.getString(META_TYPE.name)

      typ match {
        case "folder" =>
          FrFolder(uri)
        case "jrxml" =>
          FrJrxmlFile(uri)
        case _ =>
          throw new RuntimeException(s"Unimplemented eorr: type: $typ")
      }
    }
    else {
      throw new FileNotFoundException(s"Resource dosn't exist: $uri")
    }
  }

  def apply(uri: String, typ: String)(implicit context: FileRepositoryContext): FrFile = {
    val file = new File(context.root, uri)
    typ match {
      case "folder" =>
        FrFolder(file)
      case "jrxml" =>
        FrJrxmlFile(file)
      case _ =>
        throw new RuntimeException(s"Unimplemented eorr: type: $typ")
    }
  }
}

abstract class FrFile(file: File, typ: String)(implicit context: FileRepositoryContext) {
  val metaFile = new File(file, METAFILENAME)
  val contentFile = new File(file, CONTENTFILENAME)

  val uri: String = {
    val path = file.getCanonicalPath.substring(context.root.getCanonicalPath.length)
    if (path.isEmpty) "/" else path
  }

  lazy val meta: Config = {
    if (!metaFile.exists()) {
      writeMeta0(Map.empty)
    }
    ConfigFactory.parseFile(metaFile)
  }

  protected def capitalizedFirstLetter(str: String): String = {
    if (str.length == 1) {
      str.toUpperCase
    }
    else if (str.length > 1) {
      str.substring(0, 1).toUpperCase + str.substring(1)
    }
    else {
      str
    }
  }

  protected def writeMeta0(origin: Map[String, Any]): Unit = {
    val map = mutable.Map(origin.toSeq: _*)

    map ~: META_URI(uri)
    map ~: META_VERSION(0)
    map ~: META_PERMISSIONMASK(1)
    map ~: META_TYPE(typ)
    map ~: META_CREATIONTIME(file.lastModified)
    map ~: META_UPDATETIME(System.currentTimeMillis)
    map ~: META_URI(uri)

    if (!(map ?: META_LABEL)) {
      val label = if (uri == "/") "Oranizations" else capitalizedFirstLetter(file.getName)
      map =: META_LABEL(label)
    }

    if (map ?: META_VERSION) {
      map =: META_VERSION((map :: META_VERSION) + 1)
    }
    else {
      map =: META_VERSION(0)
    }

    val opt = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
    val metaString = ConfigFactory.parseMap(map.asJava).root.render(opt)
    val w = new OutputStreamWriter(new FileOutputStream(metaFile))
    w.write(metaString)
    w.close()
  }

  protected def writeContent0(buff: Array[Byte]): Unit = {
    val out = new FileOutputStream(contentFile)
    out.write(buff)
    out.close()
  }

  type R <: Resource

  def asResource: R

  def write(content: Array[Byte], meta: Map[String, Any]): Unit

  def fileType: String = meta.getString(META_TYPE)

  def exists: Boolean = file.exists()

  def delete(): Boolean = {
    def delete_file(dir: File): Boolean = {
      dir.listFiles().toSeq.foreach { f =>
        if (f.isDirectory) delete_file(f)
        else f.delete()
      }
      dir.delete()
    }
    delete_file(file)
  }

}

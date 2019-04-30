package com.thing2x.rptsvr.repo.fs

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.util.Base64

import akka.http.scaladsl.model.MediaType.{Compressible, NotCompressible}
import akka.http.scaladsl.model.{ContentType, HttpCharsets, MediaType, MediaTypes}
import com.thing2x.rptsvr._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser

import scala.io.Source
import scala.language.implicitConversions

object FsFile {
  def apply(uri: String)(implicit context: FileRepositoryContext): FsFile =
    new FsFile(new File(context.root, uri))

  def apply(file: File)(implicit context: FileRepositoryContext): FsFile =
    new FsFile(file)

  private val mimeTypes: Map[String, ContentType] = Map(
    "pdf" -> ContentType.Binary(MediaTypes.`application/pdf`),
    "html" -> ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
    "xls" -> ContentType(MediaType.applicationBinary("xls", Compressible)),
    "rtf" -> ContentType(MediaType.applicationBinary("rtf", Compressible)),
    "csv" -> ContentType(MediaTypes.`text/csv`, HttpCharsets.`UTF-8`),
    "ods" -> ContentType(MediaTypes.`application/vnd.oasis.opendocument.spreadsheet`),
    "odt" -> ContentType(MediaTypes.`application/vnd.oasis.opendocument.text`),
    "txt" -> ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`),
    "docx" -> ContentType(MediaTypes.`application/vnd.openxmlformats-officedocument.wordprocessingml.document`),
    "xlsx" -> ContentType(MediaTypes.`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`),
    "jrxml" -> ContentType(MediaType.applicationWithFixedCharset("jrxml", HttpCharsets.`UTF-8`)),
    "jar" -> ContentType(MediaTypes.`application/zip`),
    "prop" -> ContentType(MediaType.applicationWithFixedCharset("properties", HttpCharsets.`UTF-8`)),
    "jrtx" -> ContentType(MediaType.applicationWithFixedCharset("jrtx", HttpCharsets.`UTF-8`)),
    "xml" -> ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`),
    "css" -> ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`),
    "accessGrantSchema" -> ContentType(MediaType.applicationWithFixedCharset("accessGrantSchema", HttpCharsets.`UTF-8`)),
    "olapMondrianSchema" -> ContentType(MediaType.applicationWithFixedCharset("olapMondrianSchema", HttpCharsets.`UTF-8`)),
  )

  private[FsFile] def mimeTypeOf(fileType: String, filename: String): ContentType = {
    fileType match {
      case "img" if filename.endsWith(".png") => ContentType.Binary(MediaType.image("png", NotCompressible, "png"))
      case "img" if filename.endsWith(".gif") => ContentType.Binary(MediaType.image("gif", NotCompressible, "gif"))
      case "img" if filename.endsWith(".jpg") => ContentType.Binary(MediaType.image("jpeg", NotCompressible, "jpg"))
      case "img" if filename.endsWith(".jpeg") => ContentType.Binary(MediaType.image("jpeg", NotCompressible, "jpg"))
      case "font" if filename.endsWith(".ttf") => ContentType.Binary(MediaType.font("ttf", NotCompressible, "ttf"))
      case _ => if (mimeTypes.contains(fileType)) mimeTypes(fileType) else MediaType.applicationBinary("octet-stream", Compressible)
    }
  }
}

class FsFile(file: File)(implicit context: FileRepositoryContext) extends ResourceWriter with StrictLogging {

  val metaFile = new File(file, METAFILENAME)

  val contentFile = new File(file, CONTENTFILENAME)

  val uri: String = {
    val path = file.getCanonicalPath.substring(context.root.getCanonicalPath.length)
    if (path.isEmpty) "/" else path
  }

  val name: String = file.getName


  ////////////////////////////////
  // "Lazy" area
  // `meta' should be accessed when the resource physically exists
  lazy private val meta: Config = ConfigFactory.parseFile(metaFile)
  lazy val resourceType: String = meta.getString(META_RESOURCETYPE)
  // end of Lazy
  ////////////////////////////////

  def contentType: ContentType = {
    val typ = meta.getString(META_FILETYPE)
    FsFile.mimeTypeOf(typ, name)
  }

  def asResource: Resource = {
    val src = Source.fromFile(metaFile, "utf-8")
    val rsc = Resource(io.circe.parser.parse(src.mkString).right.get)
    src.close()
    rsc.right.get
  }

  def writeMeta(resource: Resource): Unit = {
    logger.trace(s"write meta uri=$uri")
    val json = resource.asMeta
    /// write meta file
    val w = new OutputStreamWriter(new FileOutputStream(metaFile))
    w.write(json.spaces2)
    w.close()
  }

  def writeContent(base64Content: String): Unit = {
    logger.trace(s"write content uri=$uri")
    /// write base64 encoded content to the file
    val buff = Base64.getDecoder.decode(base64Content)
    val out = new FileOutputStream(contentFile)
    out.write(buff)
    out.close()
  }

  def exists: Boolean = file.exists() && metaFile.exists()

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

  def mkdir(label: String): Boolean = {
    file.mkdirs()
    if (file.exists() && file.canWrite) {
      val json = parser.parse(
        s"""
          |{
          |  "uri": "$uri",
          |  "label": "$label",
          |  "version": 0,
          |  "permissionMask": 1,
          |  "resourceType": "folder"
          |}
        """.stripMargin).right.get

      val resource = Resource(json).right.get
      context.repository.setResource(uri, resource, true, true)
      true
    }
    else {
      false
    }
  }

  def list: Seq[FsFile] = {
    file.listFiles().flatMap{ child =>
      val childMetaFile = new File(child, METAFILENAME)

      if (child.isDirectory && childMetaFile.exists()) {
        Some(FsFile(child))
      }
      else {
        None
      }
    }
  }
}

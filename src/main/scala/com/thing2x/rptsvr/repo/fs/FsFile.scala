package com.thing2x.rptsvr.repo.fs

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.util.{Base64, Date}

import akka.http.scaladsl.model.MediaType.{Compressible, NotCompressible}
import akka.http.scaladsl.model.{ContentType, HttpCharsets, MediaType, MediaTypes}
import com.thing2x.rptsvr.{FileResource, FolderResource, JrxmlFile, JrxmlResources, ReportUnitResource, Resource}
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.collection.JavaConverters._
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

class FsFile(file: File)(implicit context: FileRepositoryContext) {

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
    resourceType match {
      case "folder" =>
        FolderResource(
          meta.getString(META_URI),
          meta.getString(META_LABEL),
          meta.getInt(META_PERMISSIONMASK),
          meta.getOptionString(META_DESCRIPTION),
          meta.getInt(META_VERSION),
          context.datetimeFormat.format(new Date(meta.getLong(META_CREATIONTIME))),
          context.datetimeFormat.format(new Date(meta.getLong(META_UPDATETIME))),
          "folder"
        )
      case "file" =>
        FileResource(
          meta.getString(META_URI),
          meta.getString(META_LABEL),
          meta.getInt(META_PERMISSIONMASK),
          meta.getOptionString(META_DESCRIPTION),
          meta.getInt(META_VERSION),
          context.datetimeFormat.format(new Date(meta.getLong(META_CREATIONTIME))),
          context.datetimeFormat.format(new Date(meta.getLong(META_UPDATETIME))),
          //"file",
          meta.getString(META_FILETYPE)
        )
      case "reportunit" =>
        ReportUnitResource(
          meta.getString(META_URI),
          meta.getString(META_LABEL),
          meta.getInt(META_PERMISSIONMASK),
          meta.getOptionString(META_DESCRIPTION),
          meta.getInt(META_VERSION),
          context.datetimeFormat.format(new Date(meta.getLong(META_CREATIONTIME))),
          context.datetimeFormat.format(new Date(meta.getLong(META_UPDATETIME))),
          "reportunit",
          Seq.empty,
          meta.getBoolean(META_ALWAYSPROMPTCONTROLS),
          meta.getString(META_CONTROLRAYOUT),
          JrxmlFile(FsFile(meta.getString("jrxml.jrxmlFile.uri")).asResource),
          JrxmlResources(Seq.empty)
        )
      case "jndiDataSource" => ???
      case "jdbcDataSource" => ???
      case "awsDataSource" => ???
      case "virtualDataSource" => ???
      case "beanDataSource" => ???
      case "dataType" => ???
      case "query" => ???
    }
  }

  def write(resourceType: String, body: Config): Unit = {
    file.mkdirs()

    // save creationDate (String) as creationTime (Long)
    val creationtime = if (body.hasPath(META_CREATIONTIME)) {
      body.getLong(META_CREATIONTIME)
    }
    else {
      if (body.hasPath(META_CREATIONDATE))
        context.datetimeFormat.parse(body.getString(META_CREATIONDATE)).getTime
      else
        System.currentTimeMillis()
    }

    // save updateDate (String) as updateTime (Long)
    val updatetime = if (body.hasPath(META_UPDATETIME)) {
      body.getLong(META_UPDATETIME)
    }
    else {
      if (body.hasPath(META_UPDATEDATE))
        context.datetimeFormat.parse(body.getString(META_CREATIONDATE)).getTime
      else
        creationtime
    }

    // increase versiopn number
    val version = if (body.hasPath(META_VERSION) && body.getInt(META_VERSION) == -1) 0 else body.getInt(META_VERSION)

    val additionalFields = Map[String, Any](
      META_RESOURCETYPE(resourceType),
      META_VERSION(version),
      META_CREATIONTIME(creationtime),
      META_UPDATETIME(updatetime))

    // content
    val content = body.getOptionString(META_CONTENT)

    // if report unit
    body.getOptionConfig("jrxml.jrxmlFile") match {
      case Some(jrxml) =>
        val jrxmlFile = FsFile(jrxml.getString("uri"))
        jrxmlFile.write("file", jrxml)
      case _ =>
    }

    // additional fields, removed fields
    val finalMeta = ConfigFactory.parseMap(additionalFields.asJava).withFallback(body)
      .withoutPath(META_CREATIONDATE)
      .withoutPath(META_CREATIONDATE)
      .withoutPath(META_CONTENT)
      .withoutPath("jrxml.jrxmlFile.content")

    /// write meta file
    val opt = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
    val metaString = finalMeta.root.render(opt)
    val w = new OutputStreamWriter(new FileOutputStream(metaFile))
    w.write(metaString)
    w.close()

    /// write content file
    if (content.isDefined) {
      val buff = Base64.getDecoder.decode(content.get)
      val out = new FileOutputStream(contentFile)
      out.write(buff)
      out.close()
    }
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
      write("folder", ConfigFactory.parseString(
        s"""
           |uri=$uri
           |label=$label
           |version=0
           |permissionMask=1
             """.stripMargin))
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

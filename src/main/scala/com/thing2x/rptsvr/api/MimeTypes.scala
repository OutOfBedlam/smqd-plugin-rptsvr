package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.MediaType.{Compressible, NotCompressible}
import akka.http.scaladsl.model.{ContentType, HttpCharsets, MediaType, MediaTypes}

object MimeTypes {
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

  def mimeTypeOf(fileType: String, filename: String): ContentType = {
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

package com.thing2x.rptsvr

import java.io.File

import akka.http.scaladsl.model.ContentType

case class FileContent(uri: String, file: File, contentType: ContentType)
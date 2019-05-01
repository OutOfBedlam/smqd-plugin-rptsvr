package com.thing2x.rptsvr

import akka.http.scaladsl.model.ContentType
import akka.stream.scaladsl.Source
import akka.util.ByteString

case class FileContent(uri: String, source: Source[ByteString, _], contentType: ContentType)
package com.thing2x.rptsvr

import akka.http.scaladsl.model.{HttpCharsets, MediaType}

trait ResourceMediaTypes {
  val `application/json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("json", HttpCharsets.`UTF-8`)
  val `application/repository.folder+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.folder+json", HttpCharsets.`UTF-8`)
  val `application/repository.file+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.file+json", HttpCharsets.`UTF-8`)
  val `application/repository.resourceLookup+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.resourceLookup+json", HttpCharsets.`UTF-8`)
  val `application/repository.reportunit+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.reportunit+json", HttpCharsets.`UTF-8`)
  val `application/repository.jdbcDataSource+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("repository.jdbcDataSource+json", HttpCharsets.`UTF-8`)

  def resourceMediaTypes: List[MediaType.WithFixedCharset] = List(
    `application/json`,
    `application/repository.folder+json`,
    `application/repository.file+json`,
    `application/repository.resourceLookup+json`,
    `application/repository.reportunit+json`,
    `application/repository.jdbcDataSource+json`,
  )

  def mediaTypeFromString(mediaType: String): MediaType = {
    val filtered = resourceMediaTypes.filter { mt =>
      s"${mt.mainType}/${mt.subType}".equalsIgnoreCase(mediaType)
    }

    if (filtered.isEmpty) `application/json` else filtered.head
  }
}

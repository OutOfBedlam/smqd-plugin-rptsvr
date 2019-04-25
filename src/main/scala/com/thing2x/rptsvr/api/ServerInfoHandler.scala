package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import com.thing2x.rptsvr.api.ServerInfoHandler.ServerInfo
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

object ServerInfoHandler {
  case class ServerInfo(version: String,
                        build: String,
                        edition: String,
                        editionName: String,
                        features: String,
                        licenseType: String,
                        expiration: String,
                        dateFormatPattern: String,
                        datetimeFormatPattern: String)
}

class ServerInfoHandler()(implicit executionContex: ExecutionContext) {

  def getServerInfo: Future[(StatusCode, Json)] = Future {
    val info = ServerInfo(
      version="7.1.0",
      build="20180427_1213",
      edition="PRO",
      editionName="Professional",
      features = "EXP Fusion AHD DB ",
      licenseType = "Commercial",
      expiration = "2020-02-15T07:59:00",
      dateFormatPattern = "yyyy-MM-dd",
      datetimeFormatPattern = "yyyy-MM-dd'T'HH:mm:ss",
    )
    (StatusCodes.OK, info.asJson)
  }
}

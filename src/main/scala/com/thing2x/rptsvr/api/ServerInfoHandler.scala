// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

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

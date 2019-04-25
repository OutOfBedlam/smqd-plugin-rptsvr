package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.{HttpCharsets, MediaType, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.rptsvr.{ResourceHandler, ServerInfoHandler, UserHandler}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.util.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.collection.immutable.Seq

class RestV2Controller(name: String, context: HttpServiceContext) extends RestController(name, context) with Directives with FailFastCirceSupport {

  override def mediaTypes: Seq[MediaType.WithFixedCharset] = List(
    MediaType.applicationWithFixedCharset("json", HttpCharsets.`UTF-8`),
    MediaType.applicationWithFixedCharset("repository.folder+json", HttpCharsets.`UTF-8`))

  override def routes: Route = serverInfo ~ users ~ resources

  import context.smqdInstance.Implicit._
  private val serverInfoHandler = new ServerInfoHandler
  private val userHandler = new UserHandler
  private val repositoryHandler = new ResourceHandler

  private def serverInfo: Route = {
    ignoreTrailingSlash {
      path("serverInfo") {
        get {
          val result = serverInfoHandler.getServerInfo.map(_.asJson)
          complete(StatusCodes.OK, result)
        }
      }
    }
  }

  private def users: Route = {
    ignoreTrailingSlash {
      path("users" / Segment) { username =>
        get {
          val result = userHandler.getUser(username).map( u => Json.obj(("user", u.asJson)))
          complete(StatusCodes.OK, result)
        }
      }
    }
  }


  private def resources: Route = {
    path("resources" / Segment.? ) { path =>
      get {
        parameters('expanded.as[Boolean].?) { expanded =>
          headerValueByName("Accept") {
            case "application/repository.folder+json" =>
              val result = repositoryHandler.getResources("/"+path.getOrElse(""), expanded.getOrElse(false))
                .map(_.asJson)
              complete(StatusCodes.OK, result)
            case accept =>
              complete(StatusCodes.InternalServerError, s"Unhandled accept: $accept")
          }
        }
      }
    }
  }

}

package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.{MediaType, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.ByteString
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.util.FailFastCirceSupport
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json

import scala.concurrent.duration._

class RestV2Controller(name: String, context: HttpServiceContext) extends RestController(name, context)
  with Directives with FailFastCirceSupport with LazyLogging {

  import context.smqdInstance.Implicit._
  private val serverInfoHandler = new ServerInfoHandler
  private val userHandler = new UserHandler
  private val resourceHandler = new ResourceHandler(context.smqdInstance)

  override def mediaTypes: List[MediaType.WithFixedCharset] = resourceMediaTypes

  val restVersion: String = "rest_v2"

  override def routes: Route = {
    (path(restVersion / "serverInfo") & ignoreTrailingSlash) {
      get {
        complete(serverInfoHandler.getServerInfo)
      }
    } ~
    path(restVersion / "users" / Segment) { username =>
      get {
        complete(userHandler.getUser(username, None))
      }
    } ~
    path(restVersion / "organizations"/ Segment / "users" / Segment) { (organization, username) =>
      get {
        complete(userHandler.getUser(username, Some(organization)))
      }
    } ~
    path(restVersion / "resources" / Segments ) { path =>
      val uri = path.mkString("/", "/", "")

      (get & parameters('expanded.as[Boolean].?)) { expanded =>
        complete(resourceHandler.getResource(uri, expanded))
      } ~
      (post & parameters('createFolders.as[Boolean], 'overwrite.as[Boolean]) & extract(_.request.entity)) { (createFolders, overwrite, content) =>
        entity(as[Json]) { json =>
          complete(resourceHandler.setResource(uri, content.contentType, json, createFolders, overwrite))
        }
      } ~
      delete {
        complete(resourceHandler.deleteResource(uri))
      }
    } ~
    path( restVersion / "resources") {
      (get & parameters('folderUri, 'recursive.as[Boolean], 'sortBy, 'limit.as[Int])) { (uri, recursive, sortBy, limit) =>
        complete(resourceHandler.lookupResource(uri, recursive, sortBy, limit))
      }
    } ~
    path( "j_spring_security_check") {
      ignoreTrailingSlash {
        parameters( 'j_username, 'j_password, 'forceDefaultRedirect.as[Boolean].?, 'userLocale.?, 'userTimezone.?) {
          case (username, password, forceDefaultRedirect, userLocale, userTimezone) =>
            complete(StatusCodes.OK, Json.obj(("success", Json.fromBoolean(true))))
          case _ =>
            complete(StatusCodes.BadRequest)
        } ~
          parameters('ticket) { ticket =>
            complete(StatusCodes.InternalServerError)
          }
      }
    } ~
    {
      extract(_.request) { request =>
        logger.error("Unhandled-request-1 dump begin  -----------------------------")
        logger.error(s"${request.method.toString} ${request.uri} ${request.entity.contentType.mediaType.toString}")
        val buff = request.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
        val body = scala.concurrent.Await.result(buff, 5.second)
        logger.error(s"${body.utf8String}")
        logger.error("Unhandled-request-1 dump end  -----------------------------")
        complete(StatusCodes.NotAcceptable)
      }
    }
  }
}

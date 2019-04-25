package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.server.{Directives, Route}
import com.thing2x.rptsvr.ResourceMediaTypes
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.util.FailFastCirceSupport

class RestV2Controller(name: String, context: HttpServiceContext) extends RestController(name, context)
  with Directives with FailFastCirceSupport with ResourceMediaTypes {

  import context.smqdInstance.Implicit._
  private val serverInfoHandler = new ServerInfoHandler
  private val userHandler = new UserHandler
  private val resourceHandler = new ResourceHandler(context.smqdInstance)

  override def mediaTypes: List[MediaType.WithFixedCharset] = resourceMediaTypes

  override def routes: Route = {
    (path("serverInfo") & ignoreTrailingSlash) {
      get {
        complete(serverInfoHandler.getServerInfo)
      }
    } ~
    path("users" / Segment) { username =>
      get {
        complete(userHandler.getUser(username, None))
      }
    } ~
    path("organizations"/ Segment / "users" / Segment) { (organization, username) =>
      get {
        complete(userHandler.getUser(username, Some(organization)))
      }
    } ~
    path("resources" / Segments ) { path =>
      val uri = path.mkString("/", "/", "")

      (get & parameters('expanded.as[Boolean].?) & headerValueByName("Accept")) { (expanded, accept) =>
        complete(resourceHandler.getResource(uri, expanded.getOrElse(false), mediaTypeFromString(accept)))
      } ~
      (post & parameters('createFolders.as[Boolean], 'overwrite.as[Boolean]) & extract(_.request.entity)) { (createFolders, overwrite, content) =>
        complete(resourceHandler.createResource(uri, content, createFolders, overwrite))
      } ~
      delete {
        complete(resourceHandler.deleteResource(uri))
      }
    } ~
    path( "resources") {
      (get & parameters('folderUri, 'recursive.as[Boolean], 'sortBy, 'limit.as[Int])) { (uri, recursive, sortBy, limit) =>
        complete(resourceHandler.listFolder(uri, recursive, sortBy, limit))
      }
    }
  }
}

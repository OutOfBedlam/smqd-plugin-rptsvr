package com.thing2x.rptsvr.api

import akka.http.scaladsl.model.{HttpCharsets, MediaType, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.ByteString
import com.thing2x.rptsvr.ResourceHandler.DSJdbcResource
import com.thing2x.rptsvr.{ResourceCodec, ResourceHandler, ServerInfoHandler, UserHandler}
import com.thing2x.smqd.net.http.HttpServiceContext
import com.thing2x.smqd.rest.RestController
import com.thing2x.smqd.util.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, parser}

import scala.collection.immutable.Seq

class RestV2Controller(name: String, context: HttpServiceContext) extends RestController(name, context)
  with Directives with FailFastCirceSupport with ResourceCodec {

  private val `application/json` = MediaType.applicationWithFixedCharset("json", HttpCharsets.`UTF-8`)
  private val `application/repository.folder+json` = MediaType.applicationWithFixedCharset("repository.folder+json", HttpCharsets.`UTF-8`)
  private val `application/repository.reportunit+json` = MediaType.applicationWithFixedCharset("repository.reportunit+json", HttpCharsets.`UTF-8`)
  private val `application/repository.jdbcDataSource+json` = MediaType.applicationWithFixedCharset("repository.jdbcDataSource+json", HttpCharsets.`UTF-8`)

//  import akka.http.scaladsl.settings.{ParserSettings, ServerSettings}
//  private val parserSettings = ParserSettings(context.smqdInstance.Implicit.system).withCustomMediaTypes(`application/repository.jdbcDataSource+json`)
//  private val serverSettings = ServerSettings(context.smqdInstance.Implicit.system).withParserSettings(parserSettings)

  override def mediaTypes: Seq[MediaType.WithFixedCharset] = List(
    `application/json`,
    `application/repository.folder+json`,
    `application/repository.reportunit+json`,
    `application/repository.jdbcDataSource+json`,
  )

  override def routes: Route = serverInfo ~ users ~ resources

  import context.smqdInstance.Implicit._
  private val serverInfoHandler = new ServerInfoHandler
  private val userHandler = new UserHandler
  private val resourceHandler = new ResourceHandler(context.smqdInstance)

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
          val result = userHandler.getUser(username, None).map( _.asJson )
          complete(StatusCodes.OK, result)
        }
      } ~
        path("organizations"/ Segment / "users" / Segment) { (organization, username) =>
          get {
            val result = userHandler.getUser(username, Some(organization)).map( _.asJson )
            complete(StatusCodes.OK, result)
          }
        }
    }
  }


  private def resources: Route = {
    path("resources" / Segments ) { path =>
      get {
        parameters('expanded.as[Boolean].?) { expanded =>
          headerValueByName("Accept") {
            case "application/repository.folder+json" =>
              val result = resourceHandler.getResource(path.mkString("/", "/", ""), expanded.getOrElse(false))
                .map(_.asJson)
              complete(StatusCodes.OK, result)
            case "application/repository.reportunit+json" =>
              val result = resourceHandler.getResource(path.mkString("/", "/", ""), expanded.getOrElse(false))
                .map(_.asJson)
              complete(StatusCodes.OK, result)
            case accept =>
              complete(StatusCodes.InternalServerError, s"Unhandled accept: $accept")
          }
        }
      }
    } ~
    path( "resources") {
      get {
        parameters('folderUri, 'recursive.as[Boolean], 'sortBy, 'limit.as[Int]) { (folderUri, recursive, sortBy, limit) =>
          val result = resourceHandler.listFolder(folderUri, recursive, sortBy, limit).map( r => Json.obj(("resourceLookup", r.asJson)) )
          complete(StatusCodes.OK, result)
        }
      }
    } ~
    path("resources" / Segments) { path =>
      (post & parameters('createFolders.as[Boolean], 'overwrite.as[Boolean])) { (createFolders, overwrite) =>
        extract(_.request.entity) { content =>
          val contentType = content.contentType
          contentType.mediaType match {
            case `application/repository.jdbcDataSource+json` =>
              val result = content.dataBytes.runFold(ByteString.empty)( _ ++ _).map { bstr =>
                parser.parse(bstr.utf8String).right.get
              } map { json =>
                val ds = json.as[DSJdbcResource].right.get
                resourceHandler.storeResource(path.mkString("/", "/", ""), ds, createFolders, overwrite)
              }
              complete(StatusCodes.OK, result)
            case other =>
              complete(StatusCodes.InternalServerError, s"unhandled content type: $other")
          }
        }
      }
    }
  }

}

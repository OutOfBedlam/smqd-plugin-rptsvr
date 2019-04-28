package com.thing2x.rptsvr.api

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestKit
import com.thing2x.smqd.net.http.HttpService
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.Promise
import scala.concurrent.duration._

class RestV2Test extends FlatSpec with ScalatestRouteTest with BeforeAndAfterAll with Matchers with StrictLogging {

  val config: Config = ConfigFactory.parseString(
    """
      |akka.actor.provider=local
      |smqd {
      |  report-api.config.local.port = 0
      |  plugin{
      |    static = [
      |      "./target/scala-2.12/classes",
      |    ]
      |  }
      |}
    """.stripMargin)
    .withFallback(ConfigFactory.parseFile(new File("./src/test/conf/rptsvr.conf")))
    .withFallback(ConfigFactory.parseResources("smqd-ref.conf")).resolve()

  var smqdInstance: Smqd = _
  var routes: Route = _
  var shutdownPromose: Promise[Boolean] = Promise[Boolean]

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

  override def beforeAll(): Unit = {
    smqdInstance = new SmqdBuilder(config).setActorSystem(system).build()
    smqdInstance.start()
    routes = smqdInstance.service("report-api").get.asInstanceOf[HttpService].routes
  }

  override def afterAll(): Unit = {
    shutdownPromose.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  val rsname = "unit_test1"

  "reportunit" should "be written" in {
    val reportUnitReq = HttpEntity(ContentType(`application/repository.reportunit+json`),
      s"""
        |{
        |  "alwaysPromptControls":false,
        |  "controlsLayout":"popupScreen",
        |  "creationDate":"2019-04-27T08:31:07",
        |  "description":"",
        |  "inputControls":[],
        |  "jrxml":{
        |    "jrxmlFile":{
        |      "content":"PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPCEtLSBDcmVhdGVkIHdpdGggSmFzcGVyc29mdCBTdHVkaW8gdmVyc2lvbiA2LjguMC5maW5hbCB1c2luZyBKYXNwZXJSZXBvcnRzIExpYnJhcnkgdmVyc2lvbiA2LjguMC0yZWQ4ZGZhYmI2OTBmZjMzN2E1Nzk3MTI5ZjJjZDkyOTAyYjBjODdiICAtLT4KPGphc3BlclJlcG9ydCB4bWxucz0iaHR0cDovL2phc3BlcnJlcG9ydHMuc291cmNlZm9yZ2UubmV0L2phc3BlcnJlcG9ydHMiIHhtbG5zOnhzaT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEtaW5zdGFuY2UiIHhzaTpzY2hlbWFMb2NhdGlvbj0iaHR0cDovL2phc3BlcnJlcG9ydHMuc291cmNlZm9yZ2UubmV0L2phc3BlcnJlcG9ydHMgaHR0cDovL2phc3BlcnJlcG9ydHMuc291cmNlZm9yZ2UubmV0L3hzZC9qYXNwZXJyZXBvcnQueHNkIiBuYW1lPSJTaW1wbGVfUmVwb3J0IiBwYWdlV2lkdGg9IjU5NSIgcGFnZUhlaWdodD0iODQyIiBjb2x1bW5XaWR0aD0iNTU1IiBsZWZ0TWFyZ2luPSIyMCIgcmlnaHRNYXJnaW49IjIwIiB0b3BNYXJnaW49IjMwIiBib3R0b21NYXJnaW49IjMwIiB1dWlkPSJiYTY5MWYwYy0yMDZhLTQxMGYtYmRhMy03ODI3OGMyMjBiMzIiPgoJPHByb3BlcnR5IG5hbWU9ImlyZXBvcnQuamFzcGVyc2VydmVyLnVybCIgdmFsdWU9Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9ycHRzdnIvIi8+Cgk8cHJvcGVydHkgbmFtZT0iaXJlcG9ydC5qYXNwZXJzZXJ2ZXIudXNlciIgdmFsdWU9InVzZXJuYW1lIi8+Cgk8cHJvcGVydHkgbmFtZT0iaXJlcG9ydC5qYXNwZXJzZXJ2ZXIucmVwb3J0LnJlc291cmNlIiB2YWx1ZT0iL1Rlc3QvcmVwb3J0MSIvPgoJPHByb3BlcnR5IG5hbWU9ImNvbS5qYXNwZXJzb2Z0LnN0dWRpby5kYXRhLmRlZmF1bHRkYXRhYWRhcHRlciIgdmFsdWU9Ik9uZSBFbXB0eSBSZWNvcmQiLz4KCTxwYXJhbWV0ZXIgbmFtZT0iR1JFRVRJTkciIGNsYXNzPSJqYXZhLmxhbmcuU3RyaW5nIi8+Cgk8dGl0bGU+CgkJPGJhbmQgaGVpZ2h0PSI2MSIgc3BsaXRUeXBlPSJTdHJldGNoIj4KCQkJPHRleHRGaWVsZD4KCQkJCTxyZXBvcnRFbGVtZW50IHg9IjE4MCIgeT0iMCIgd2lkdGg9IjIwMCIgaGVpZ2h0PSI1MCIgdXVpZD0iMjNhNmYxNDUtZThjZi00NDNkLWI4MWMtYmNiYzRmZWJkZDg3Ii8+CgkJCQk8dGV4dEZpZWxkRXhwcmVzc2lvbj48IVtDREFUQVskUHtHUkVFVElOR31dXT48L3RleHRGaWVsZEV4cHJlc3Npb24+CgkJCTwvdGV4dEZpZWxkPgoJCQk8c3RhdGljVGV4dD4KCQkJCTxyZXBvcnRFbGVtZW50IHg9IjQ3MCIgeT0iNDAiIHdpZHRoPSIxMDAiIGhlaWdodD0iMTkiIGZvcmVjb2xvcj0iI0UzMDAyNSIgdXVpZD0iODllYjYyNjctZGY2Zi00M2U0LThlYmEtMGI1YTlmMTMxOTNjIi8+CgkJCQk8dGV4dD48IVtDREFUQVtIZWxsbyBXb3JsZCFdXT48L3RleHQ+CgkJCTwvc3RhdGljVGV4dD4KCQk8L2JhbmQ+Cgk8L3RpdGxlPgo8L2phc3BlclJlcG9ydD4K",
        |      "label":"Main jrxml",
        |      "permissionMask":1,
        |      "type":"jrxml",
        |      "uri":"/${rsname}_files/main_jrxml","version":-1
        |    }
        |  },
        |  "label":"$rsname",
        |  "permissionMask":1,
        |  "resources":{
        |    "resource":[]
        |  },
        |  "updateDate":"2019-04-27T09:08:11",
        |  "uri":"/$rsname",
        |  "version":1
        |}
      """.stripMargin
    )
    Post(s"/rptsvr/rest_v2/resources/$rsname?createFolders=true&overwrite=true", reportUnitReq) ~> routes ~> check {
      status shouldEqual StatusCodes.Created
    }
  }

  it should "retrieve" in {
    Get(s"/rptsvr/rest_v2/resources/$rsname?expanded=true").withHeaders(RawHeader("Accept", "application/repository.file+json")) ~> routes ~> check {
      logger.info(">> "+entityAs[String])
      status shouldEqual StatusCodes.OK
    }
  }
}

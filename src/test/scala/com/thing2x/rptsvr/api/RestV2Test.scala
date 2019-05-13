package com.thing2x.rptsvr.api

import java.io.File
import java.sql.Driver
import java.util.{Base64, Properties}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentType, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestKit
import com.thing2x.rptsvr.engine.ReportEngine.ExportFormat
import com.thing2x.rptsvr.engine.{Report, ReportEngine}
import com.thing2x.smqd.net.http.HttpService
import com.thing2x.smqd.{Smqd, SmqdBuilder}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Json, parser}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.io.Source

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

  var engine: ReportEngine = _
  var exportDir: File = _
  var repoDir: File = _
  val readOnly: Boolean = config.getBoolean("smqd.report-repo.config.readonly")
  val writingTest: Boolean = !readOnly

  override def createActorSystem(): ActorSystem = ActorSystem(actorSystemNameFrom(getClass), config)

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

  override def beforeAll(): Unit = {
    repoDir = new File("./src/test/repo")
    if (!repoDir.exists()) repoDir.mkdir()

    exportDir = new File("./src/test/export")
    if (!exportDir.exists) exportDir.mkdir()

    smqdInstance = new SmqdBuilder(config).setActorSystem(system).build()
    smqdInstance.start()
    routes = smqdInstance.service("report-api").get.asInstanceOf[HttpService].routes

    engine = ReportEngine.instance.get
  }

  override def afterAll(): Unit = {
    shutdownPromose.future.onComplete { _ =>
      smqdInstance.stop()
      TestKit.shutdownActorSystem(system)
    }
  }

  /////////////////////////////////////////////////
  // test jdbc connection
  "database test" should "pass" in {
    assert(Class.forName("org.h2.Driver") != null)

    val url = "jdbc:h2:tcp://localhost:9099/mem:sampledb"
    val clazz = Class.forName("org.h2.Driver")
    val driver = clazz.getDeclaredConstructor().newInstance().asInstanceOf[Driver]

    assert( driver.acceptsURL(url) )

    val props = new Properties()
    props.setProperty("user", "sa")
    props.setProperty("password", "sa")

    val conn = driver.connect(url, props)
    assert( conn != null)

    val stmt = conn.prepareStatement("select name, cost, email from sample_table")
    val rset = stmt.executeQuery()
    while( rset.next() ) {
      val name = rset.getString("NAME")
      val cost = rset.getInt("COST")
      val email = rset.getString("EMAIL")
      logger.info(s"---> $name  $cost  $email")
    }
    rset.close()
    stmt.close()
    conn.close()
  }

  /////////////////////////////////////////////////
  // create test folder
  val foldername = "unit_test_folder"
  "folder" should "be created" in {
    if (writingTest) {
      val req = HttpEntity(ContentType(`application/repository.folder+json`),
        s"""
           |{
           |  "version":-1,
           |  "permissionMask":1,
           |  "label":"$foldername",
           |  "uri":"/$foldername"
           |}
       """.stripMargin)
      Post(s"/rptsvr/rest_v2/resources/$foldername?createFolders=true&overwrite=true", req) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val content = entityAs[String]
        val json = parser.parse(content).right.get
        assert( contentType.mediaType == `application/repository.folder+json` )
        logger.info(json.spaces2)
        val cur = json.hcursor
        assert (cur.downField("uri").as[String].right.get == s"/$foldername")
        assert (cur.downField("label").as[String].isRight)
        assert (cur.downField("version").as[Int].isRight)
      }
    }
  }

  // get test folder
  it should "be retrieved" in {
    Get(s"/rptsvr/rest_v2/resources/$foldername?expanded=true") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val content = entityAs[String]
      val json = parser.parse(content).right.get
      logger.info(json.spaces2)
      val cur = json.hcursor
      assert (cur.downField("uri").as[String].right.get == s"/$foldername")
      assert (cur.downField("label").as[String].isRight)
      assert (cur.downField("version").as[Int].isRight)
    }
  }

  /////////////////////////////////////////////////
  // write image file
  val imgname = "plane.png"
  val imgBase64: String = "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"
  "image file" should "be written" in {
    if (writingTest) {
      val imgReq = HttpEntity(ContentType(`application/repository.file+json`),
        s"""
           |{
           |  "version":-1,
           |  "permissionMask":1,
           |  "label":"$imgname",
           |  "uri":"/$foldername/$imgname",
           |  "type":"img",
           |  "content":"$imgBase64"
           |}
      """.stripMargin)
      Post(s"/rptsvr/rest_v2/resources/$foldername/$imgname?createFolders=true&overwrite=true", imgReq) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val content = entityAs[String]
        val json = parser.parse(content).right.get
        logger.info(json.spaces2)
        val cur = json.hcursor
        assert (cur.downField("uri").as[String].right.get == s"/$foldername/$imgname")
      }
    }
  }

  // get image content
  it should "retrieve content" in {
    Get(s"/rptsvr/rest_v2/resources/$foldername/$imgname") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      assert(contentType.mediaType.isImage)
      assert(contentType.mediaType.subType == "png")
      val entity = entityAs[Array[Byte]]
      entity.length shouldEqual 8247
      val b64 = Base64.getEncoder.encodeToString(entity)
      b64 shouldEqual imgBase64
    }
  }

  /////////////////////////////////////////////////
  // write jdbc datasource
  val jdbcname = "database"
  "jdbc datasource" should "be written" in {
    if (writingTest) {
      val req = HttpEntity(ContentType(`application/repository.jdbcDataSource+json`),
        s"""
           |{
           |  "version" : -1,
           |  "permissionMask" : 1,
           |  "label" : "$jdbcname",
           |  "uri" : "/$foldername/$jdbcname",
           |  "driverClass" : "org.h2.Driver",
           |  "password" : "sa",
           |  "username" : "sa",
           |  "connectionUrl" : "jdbc:h2:tcp://localhost:9099/mem:sampledb"
           |}
      """.stripMargin)
      Post(s"/rptsvr/rest_v2/resources/$foldername/$jdbcname?createFolders=true&overwrite=true", req) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val content = entityAs[String]
        val json = parser.parse(content).right.get
        logger.info(json.spaces2)
        val cur = json.hcursor
        assert (cur.downField("uri").as[String].right.get == s"/$foldername/$jdbcname")
      }
    }
  }

  it should "be retrieved" in {
    Get(s"/rptsvr/rest_v2/resources/$foldername/$jdbcname?expanded=true") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val content = entityAs[String]
      val json = parser.parse(content).right.get
      logger.info(json.spaces2)
      val cur = json.hcursor
      assert (cur.downField("uri").as[String].right.get == s"/$foldername/$jdbcname")
    }
  }

  /////////////////////////////////////////////////
  // write query resource
  val queryname = "select_all_sample_table"
  "query" should "be written" in {
    if (writingTest) {
      val req = HttpEntity(ContentType(`application/repository.query+json`),
        s"""
           |{
           |  "version" : -1,
           |  "permissionMask" : 1,
           |  "label" : "$queryname",
           |  "uri" : "/$foldername/$queryname",
           |  "dataSource" : {
           |    "dataSourceReference" : {
           |      "uri" : "/$foldername/$jdbcname"
           |    }
           |  },
           |  "value" : "select * from sample_table",
           |  "language" : "sql"
           |}
       """.stripMargin)
      Post(s"/rptsvr/rest_v2/resources/$foldername/$queryname?createFolders=true&overwrite=true", req) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        val content = entityAs[String]
        val json = parser.parse(content).right.get
        logger.info(json.spaces2)
        val cur = json.hcursor
        assert (cur.downField("uri").as[String].right.get == s"/$foldername/$queryname")
      }
    }
  }

  it should "be retrieved" in {
    Get(s"/rptsvr/rest_v2/resources/$foldername/$queryname?expanded=true") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val content = entityAs[String]
      val json = parser.parse(content).right.get
      logger.info(json.spaces2)
      val cur = json.hcursor
      assert (cur.downField("uri").as[String].right.get == s"/$foldername/$queryname")
      assert (cur.downField("value").as[String].right.get == s"select * from sample_table")
    }
  }

  /////////////////////////////////////////////////
  // write reportunit
  val reportunitname = "rptunit"

  "reportunit" should "be written" in {
    if (writingTest) {
      val src = Source.fromFile(new File("./src/test/resources/sample_reportunit.json"))
      val jsonString = src.getLines().mkString("\n")
      src.close()

      val reportUnitReq = HttpEntity(ContentType(`application/repository.reportUnit+json`), jsonString)
      Post(s"/rptsvr/rest_v2/resources/$foldername/$reportunitname?createFolders=true&overwrite=true", reportUnitReq) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        assert(contentType.mediaType == `application/repository.reportUnit+json`)

        val content = entityAs[String]
        val json = parser.parse(content).right.get
        logger.info(json.spaces2)
        val cur = json.hcursor
        assert (cur.downField("uri").as[String].right.get == s"/$foldername/$reportunitname")
        assert (cur.downField("label").as[String].isRight)
        assert (cur.downField("version").as[Int].isRight)
        // check inputControls
        val inputControls = cur.downField("inputControls").as[Seq[Json]]
        assert (inputControls.isRight)
        assert (inputControls.right.get.nonEmpty)
        // check jrxml
        assert (cur.downField("jrxml").downField("jrxmlFile").succeeded)
        // check resources
        val resources = cur.downField("resources").downField("resource").as[Seq[Json]]
        assert (resources.isRight)
        assert (resources.right.get.nonEmpty)
        // check DataSource
        val dataSource = cur.downField("dataSource")
        assert (dataSource.downField("jdbcDataSource").succeeded)
      }
    }
  }

  it should "be retrieved" in {
    Get(s"/rptsvr/rest_v2/resources/$foldername/$reportunitname?expanded=false").withHeaders(RawHeader("Accept", "application/repository.file+json")) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      assert(contentType.mediaType == `application/repository.reportUnit+json`)

      val content = entityAs[String]
      val json = parser.parse(content).right.get
      logger.info(json.spaces2)

      val cur = json.hcursor
      // check inputControls
      val inputControls = cur.downField("inputControls").as[Seq[Json]]
      assert (inputControls.isRight)
      assert (inputControls.right.get.nonEmpty)
      // check jrxml
      assert (cur.downField("jrxml").downField("jrxmlFileReference").succeeded)
      // check resources
      val resources = cur.downField("resources").downField("resource").as[Seq[Json]]
      assert (resources.isRight)
      assert (resources.right.get.nonEmpty)

    }
  }

  implicit val timeout: FiniteDuration = 5.seconds
  private val params = Map("GREETING" -> "Hello-world (한국어 서체 사용)")
  private var report: Report = _

  "report unit" should "be instancitated" in {
    report = engine.report(s"/$foldername/$reportunitname")
    val tick = System.currentTimeMillis
    val jsReport = Await.result(report.compile, 5.seconds)

    logger.info(
      s"""report compile time: ${System.currentTimeMillis - tick}ms.
         |Compiler: ${jsReport.getCompilerClass}
         |${jsReport.getPropertiesMap.toString}
         |""".stripMargin)
  }

  it should "generate html" in {
    report.exportReportToFileSync(params, ExportFormat.html, new File(exportDir, "test_result.html").getPath)
  }

  it should "export as docx" in {
    report.exportReportToFileSync(params, ExportFormat.docx,  new File(exportDir, "test_result.docx").getPath)
  }

  it should "export as xls" in {
    report.exportReportToFileSync(params, ExportFormat.xls,  new File(exportDir, "test_result.xls").getPath)
  }

  it should "export as rtf" in {
    report.exportReportToFileSync(params, ExportFormat.rtf,  new File(exportDir, "test_result.rtf").getPath)
  }

  it should "export as odt" in {
    report.exportReportToFileSync(params, ExportFormat.odt,  new File(exportDir, "test_result.odt").getPath)
  }

  it should "export as xml" in {
    report.exportReportToFileSync(params, ExportFormat.xml,  new File(exportDir, "test_result.xml").getPath)
  }

  it should "export as pptx" in {
    report.exportReportToFileSync(params, ExportFormat.pptx,  new File(exportDir, "test_result.pptx").getPath)
  }

  it should "export as text" in {
    report.exportReportToFileSync(params, ExportFormat.text,  new File(exportDir, "test_result.txt").getPath)
  }

  it should "export as csv" in {
    report.exportReportToFileSync(params, ExportFormat.csv, new File(exportDir, "test_result.csv").getPath)
  }

  it should "generate pdf" in {
    report.exportReportToFileSync(params, ExportFormat.pdf, new File(exportDir, "test_result.pdf").getPath)
  }
}

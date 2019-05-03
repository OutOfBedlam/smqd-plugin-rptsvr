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

package com.thing2x.rptsvr.engine

import java.io.{File, InputStream}
import java.sql.{Connection, Driver}
import java.util.Properties

import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportEngine.ExportFormat
import com.thing2x.rptsvr.{DataSourceResource, JdbcDataSourceResource, ReportUnitResource, Repository}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine._
import net.sf.jasperreports.engine.fonts.FontFamily
import net.sf.jasperreports.extensions.ExtensionsEnvironment
import net.sf.jasperreports.repo.{PersistenceServiceFactory, RepositoryService}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object ReportEngine {
  def findInstance(smqd: Smqd): ReportEngine = {
    val engineClass = classOf[ReportEngine]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      engineClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[ReportEngine]).get
  }

  object ExportFormat extends Enumeration {
    val pdf: Value = Value("pdf")
    val html: Value = Value("html")
    val docx: Value = Value("docx")
    val csv: Value = Value("csv")
    val pptx: Value = Value("pptx")
    val json: Value = Value("json")
    val xml: Value = Value("xml")
    val xls: Value = Value("xls")
    val text: Value = Value("text")
    val rtf: Value = Value("rtf")
    val odt: Value = Value("odt")
  }
}

class ReportEngine(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  private val backend = Repository.findInstance(smqd)
  private val jsContext = new SimpleJasperReportsContext()
  val repositoryService = new EngineRepositoryService(jsContext, backend)
  jsContext.setExtensions(classOf[RepositoryService], Seq(repositoryService).asJava)
  jsContext.setExtensions(classOf[PersistenceServiceFactory], Seq(EngineRepositoryPersistenceServiceFactory).asJava)

  implicit val ec: ExecutionContext = backend.context.executionContext
  implicit val materializer: Materializer = backend.context.materializer

  override def start(): Unit = {
    val fontFamilies = ExtensionsEnvironment.getExtensionsRegistry.getExtensions(classOf[FontFamily]).asScala
    fontFamilies.foreach { fm =>
      logger.info(s"** Font '${fm.getName}' pdfEncoding='${fm.getPdfEncoding}' isPdfEmbedded=${fm.isPdfEmbedded}")
    }
  }

  override def stop(): Unit = {

  }

  def exportReport(uri: String, parameters: Map[String, Any], format: ExportFormat.Value): Future[ByteString] = {
    val exporter = ReportExporter(jsContext, format)
    generate(uri, parameters).map ( exporter.exportReport )
  }

  def exportReportToFile(uri: String, parameters: Map[String, Any], format: ExportFormat.Value, destFilename: String): Future[File] = {
    val exporter = ReportExporter(jsContext, format)
    generate(uri, parameters).map( exporter.exportReportToFile(_, destFilename))
  }

  def exportReportToFileSync(uri: String, parameters: Map[String, Any], format: ExportFormat.Value, destFilename: String)(implicit timeout: FiniteDuration): File = {
    val future = exportReportToFile(uri, parameters, format, destFilename)
    Await.result(future, timeout)
  }

  case class ReportUnitContents(jsReport: JasperReport, resources: Map[String, Array[Byte]], dataSource: Option[DataSourceResource])

  private val useReportCache = true
  private val reportCache = LruCache[String, JasperReport](20)
  private val useResourceCache = true
  private val resourceCache = LruCache[String, Array[Byte]](50)

  private def loadReportUnit(uri: String): Future[ReportUnitContents] = {
    backend.getResource(uri).map{
      case Left(ex) =>
        Future.failed(ex)
      case Right(r) if !r.isInstanceOf[ReportUnitResource] =>
        Future.failed(new RuntimeException(s"resource is not report unit, but ${r.resourceType}: $uri"))
      case Right(r) if r.isInstanceOf[ReportUnitResource] =>
        // ReportUnit loaded
        val ru = r.asInstanceOf[ReportUnitResource]
        // get jrxml file as an InputStream
        val compiledJrxml: Future[JasperReport] = ru.jrxml match {
          case Some(fr) =>
            if (useReportCache)
              reportCache.fromFuture(fr.uri, fr.updateDate.map(_.getTime)) { compileJrxml(fr.uri) }
            else
              compileJrxml(fr.uri)
          case None => Future.failed(new RuntimeException("report unit doesn't contain jrxml"))
        }

        // get resource files as Map[name, InputStream]
        val resources: Future[Map[String, Array[Byte]]] = Future.sequence(
          ru.resources map { case (resourceName, resource) =>
            if (useResourceCache) {
              val f = resourceCache.fromFuture(resource.uri, resource.updateDate.map(_.getTime)){ loadResource(resource.uri) }
              f.map( (resourceName, _) )
            }
            else {
              loadResource(resource.uri).map( (resourceName, _) )
            }
          }).map(_.toSeq).map(seq => Map(seq:_*))

        // parallelize futures of reading contents
        for {
          jrxml <- compiledJrxml
          resourceMap <- resources
        } yield ReportUnitContents(jrxml, resourceMap, ru.dataSource)
    }.flatten
  }

  private def loadResource(uri: String): Future[Array[Byte]] = {
    backend.getContent(uri).map {
      case Right(fc) =>
        logger.info(s"Resource loading $uri")
        val tick = System.currentTimeMillis
        val fbs = fc.source.runFold(ByteString.empty)( _++_).map(_.toArray)
        logger.debug(s"Resource loading time ${System.currentTimeMillis - tick}ms. $uri")
        fbs
      case Left(ex) =>
        Future.failed(ex)
    }.flatten
  }

  private def compileJrxml(uri: String): Future[JasperReport] = {
    backend.getContent(uri).map {
      case Right(fc) =>
        logger.info(s"Compiling jrxml $uri")
        val tick = System.currentTimeMillis
        val in = fc.source.runWith(StreamConverters.asInputStream(3.seconds))
        val compiler = JasperCompileManager.getInstance(jsContext)
        val report = compiler.compile(in)
        logger.debug(s"Compile time ${System.currentTimeMillis - tick}ms. $uri")
        Future.successful(report)
      case Left(ex) =>
        Future.failed(ex)
    }.flatten
  }

  private def jdbcDataSource(dsResource: Option[DataSourceResource]): Future[Option[Connection]] = Future {
    if (dsResource.isDefined && dsResource.get.isInstanceOf[JdbcDataSourceResource]) {
      val ds = dsResource.get.asInstanceOf[JdbcDataSourceResource]

      logger.debug(s"JDBC DS driver=${ds.driverClass} url=${ds.connectionUrl}")
      val clazz = Class.forName("org.h2.Driver")
      val driver = clazz.getDeclaredConstructor().newInstance().asInstanceOf[Driver]

      val props = new Properties()
      if (ds.username.isDefined) props.setProperty("user", ds.username.get)
      if (ds.password.isDefined) props.setProperty("password", ds.password.get)

      val conn = driver.connect(ds.connectionUrl.get, props)
      Option(conn)
    }
    else {
      None
    }
  }

  private def dataSource(dsResource: Option[DataSourceResource]): Future[Option[JRDataSource]] = Future{
    dsResource match {
      // TODO: create JR DataSource instance
      //Some(new JREmptyDataSource)

      // for now, we are supporting only jdbc datasource
      case _ => None
    }
  }

  private def generate(uri: String, parameters: Map[String, Any]): Future[JasperPrint] = {
    val compiled = for {
      ctnt <- loadReportUnit(uri)
      ds <- dataSource(ctnt.dataSource)
      jdbcConn <- jdbcDataSource(ctnt.dataSource)
    } yield (ctnt, ds, jdbcConn)

    compiled map { case (ctnt, dataSource, jdbcConnection) =>
      // set parameters
      val params = new java.util.HashMap[String, AnyRef]()
      parameters.foreach { case (k, v) =>
        params.put(k, v.asInstanceOf[AnyRef])
      }

      // set resources
      ctnt.resources.foreach{ case (resourceName, in) =>
        jsContext.setValue(s"repo:$resourceName", in)
      }

      // create JasperPrint by filling JasperReport
      if (dataSource.isDefined) {
        JasperFillManager.getInstance(jsContext).fill(ctnt.jsReport, params, dataSource.get)
      }
      else if (jdbcConnection.isDefined) {
        val conn = jdbcConnection.get
        try {
          JasperFillManager.getInstance(jsContext).fill(ctnt.jsReport, params, conn)
        }
        finally {
          conn.close()
        }
      }
      else {
        JasperFillManager.getInstance(jsContext).fill(ctnt.jsReport, params)
      }
    }
  }
}

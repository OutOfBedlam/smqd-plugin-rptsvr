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

import java.io.ByteArrayInputStream
import java.sql.{Connection, Driver}
import java.util.Properties

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, MediaTypes}
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.thing2x.rptsvr.{DataSourceResource, JdbcDataSourceResource, ReportUnitResource, Repository}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine._
import net.sf.jasperreports.engine.fonts.FontFamily
import net.sf.jasperreports.engine.query.QueryExecuterFactory
import net.sf.jasperreports.extensions.ExtensionsEnvironment

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object ReportEngine {
  private var _instance: Option[ReportEngine] = None
  def instance: Option[ReportEngine] = _instance

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

    def valueOf(ext: String): Option[ExportFormat.Value] = {
      ext match  {
        case "pdf" => Some(pdf)
        case "html" => Some(html)
        case "docx" => Some(docx)
        case "csv" => Some(csv)
        case "pptx" => Some(pptx)
        case "json" => Some(json)
        case "xml" => Some(xml)
        case "xls" => Some(xls)
        case "text" => Some(text)
        case "rtf" => Some(rtf)
        case "odt" => Some(odt)
        case _ => None
      }
    }

    def contentType(fmt: ExportFormat.Value): ContentType = {
      if (fmt == pdf) ContentType(MediaTypes.`application/pdf`)
      else if (fmt == html) ContentType.WithCharset(MediaTypes.`text/html`, HttpCharsets.`UTF-8`)
      else if (fmt == docx) ContentType(MediaTypes.`application/vnd.openxmlformats-officedocument.wordprocessingml.document`)
      else if (fmt == csv) ContentType.WithCharset(MediaTypes.`text/csv`, HttpCharsets.`UTF-8`)
      else if (fmt == pptx) ContentType(MediaTypes.`application/vnd.openxmlformats-officedocument.presentationml.slide`)
      else if (fmt == json) ContentType.WithFixedCharset(MediaTypes.`application/json`)
      else if (fmt == xml) ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`)
      else if (fmt == xls) ContentType(MediaTypes.`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`)
      else if (fmt == text) ContentType.WithCharset(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`)
      else if (fmt == rtf) ContentType.WithCharset(MediaTypes.`text/richtext`, HttpCharsets.`UTF-8`)
      else if (fmt == odt) ContentType(MediaTypes.`application/vnd.openxmlformats-officedocument.wordprocessingml.document`)
      else ContentTypes.`application/octet-stream`
    }
  }
}

class ReportEngine(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  ReportEngine._instance = Some(this)

  private[engine] val backend = Repository.instance.get

  implicit val ec: ExecutionContext = backend.context.executionContext
  implicit val materializer: Materializer = backend.context.materializer

  private val useReportCache = config.getBoolean("cache.report.enabled")
  private val reportCache = LruCache[String, JasperReport](
    config.getInt("cache.report.max"),
    config.getDouble("cache.report.dropfraction"),
    config.getDuration("cache.report.ttl").toMillis.millis)

  private val useResourceCache = config.getBoolean("cache.resource.enabled")
  private val resourceCache = LruCache[String, Array[Byte]](
    config.getInt("cache.resource.max"),
    config.getDouble("cache.resource.dropfraction"),
    config.getDuration("cache.resource.ttl").toMillis.millis)

  override def start(): Unit = {
    ExtensionsEnvironment.getExtensionsRegistry.getExtensions(classOf[FontFamily]).asScala.foreach { fm =>
      logger.info(s"** Font '${fm.getName}' pdfEncoding='${fm.getPdfEncoding}' isPdfEmbedded=${fm.isPdfEmbedded}")
    }

    ExtensionsEnvironment.getExtensionsRegistry.getExtensions(classOf[QueryExecuterFactory]).asScala.foreach { eq =>
      logger.info(s"== QueryExecuter '${eq.getBuiltinParameters.toString}'")
    }
  }

  override def stop(): Unit = {

  }

  def report(reportUnitUri: String): Report = {
    new Report(this, reportUnitUri)
  }

  private[engine] def loadReportUnit(report: Report): Future[ReportUnitCompiled] = {
    backend.getResource(report.reportUnitUri).map{
      case Left(ex) =>
        Future.failed(ex)
      case Right(r) if !r.isInstanceOf[ReportUnitResource] =>
        Future.failed(new RuntimeException(s"resource is not report unit, but ${r.resourceType}: ${report.reportUnitUri}"))
      case Right(r) if r.isInstanceOf[ReportUnitResource] =>
        // ReportUnit loaded
        val ru = r.asInstanceOf[ReportUnitResource]
        // compile jrxml
        val compiledJrxml: Future[JasperReport] = ru.jrxml match {
          case Some(fr) =>
            if (useReportCache)
              reportCache.fromFuture(fr.uri, fr.updateDate.map(_.getTime)) {
                compileJrxml(fr.uri, report.jsContext)
              }
            else
              compileJrxml(fr.uri, report.jsContext)
          case None => Future.failed(new RuntimeException("report unit doesn't contain jrxml"))
        }

        // get resource files as Map[name, InputStream]
        val resources: Future[Map[String, Any]] = Future.sequence(
          ru.resources map { case (resourceName, resource) =>
            logger.trace(s"load report resource: $resourceName ${resource.resourceType} ${resource.fileType}")
            if (useResourceCache) {
              val f = resourceCache.fromFuture(resource.uri, resource.updateDate.map(_.getTime)){ loadResource(resource.uri) }
              f.map{ rsc =>
                val result =
                  if (resource.fileType == "jrxml") compileJrxml(rsc, report.jsContext) // sub-report
                  else rsc // other resource (i.e images)
                (resourceName, result)
              }
            }
            else {
              loadResource(resource.uri).map( (resourceName, _) )
            }
          }).map(_.toSeq).map(seq => Map(seq:_*))

        for {
          jrxml <- compiledJrxml
          resourceMap <- resources
        } yield ReportUnitCompiled(jrxml, resourceMap, ru.dataSource)
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

  def compileJrxml(buff: Array[Byte], jsContext: JasperReportsContext): JasperReport = {
    val in = new ByteArrayInputStream(buff)
    val compiler = JasperCompileManager.getInstance(jsContext)
    val report = compiler.compile(in)
    report
  }

  def compileJrxml(jrxmlUri: String, jsContext: JasperReportsContext): Future[JasperReport] = {
    backend.getContent(jrxmlUri).map {
      case Right(fc) =>
        logger.info(s"Compiling jrxml $jrxmlUri")
        val tick = System.currentTimeMillis
        val in = fc.source.runWith(StreamConverters.asInputStream(3.seconds))
        val compiler = JasperCompileManager.getInstance(jsContext)
        val report = compiler.compile(in)
        logger.debug(s"Compile time ${System.currentTimeMillis - tick}ms. $jrxmlUri")
        Future.successful(report)
      case Left(ex) =>
        Future.failed(ex)
    }.flatten
  }

  private[engine] def jdbcDataSource(dsResource: Option[DataSourceResource]): Future[Option[Connection]] = Future {
    if (dsResource.isDefined && dsResource.get.isInstanceOf[JdbcDataSourceResource]) {
      val ds = dsResource.get.asInstanceOf[JdbcDataSourceResource]

      logger.debug(s"JDBC DS driver=${ds.driverClass} url=${ds.connectionUrl}")
      val clazz = Class.forName(ds.driverClass.get)
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

  private[engine] def dataSource(dsResource: Option[DataSourceResource]): Future[Option[JRDataSource]] = Future{
    dsResource match {
      // TODO: create JR DataSource instance
      //Some(new JREmptyDataSource)
      // for now, we are supporting only jdbc datasource
      case _ =>
        //logger.info(s"Not Impl. ============+> ${dsResource}")
        None
    }
  }
}

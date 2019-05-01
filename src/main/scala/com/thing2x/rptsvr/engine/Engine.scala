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

import java.io.{ByteArrayOutputStream, File, InputStream}

import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.thing2x.rptsvr.engine.Engine.ExportFormat
import com.thing2x.rptsvr.{DataSourceResource, JdbcDataSourceResource, ReportUnitResource, Repository}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine._
import net.sf.jasperreports.engine.export._
import net.sf.jasperreports.engine.export.oasis.JROdtExporter
import net.sf.jasperreports.engine.export.ooxml.{JRDocxExporter, JRPptxExporter}
import net.sf.jasperreports.export._
import net.sf.jasperreports.repo.{PersistenceServiceFactory, RepositoryService}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Engine {
  def findInstance(smqd: Smqd): Engine = {
    val engineClass = classOf[Engine]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      engineClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[Engine]).get
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

class Engine(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  private val backend = Repository.findInstance(smqd)
  private val jsContext = new SimpleJasperReportsContext()
  val repositoryService = new EngineRepositoryService(jsContext, backend)
  jsContext.setExtensions(classOf[RepositoryService], Seq(repositoryService).asJava)
  jsContext.setExtensions(classOf[PersistenceServiceFactory], Seq(EngineRepositoryPersistenceServiceFactory).asJava)

  implicit val ec: ExecutionContext = backend.context.executionContext
  implicit val materializer: Materializer = backend.context.materializer

  override def start(): Unit = {
  }

  override def stop(): Unit = {

  }

  def exportReport(uri: String, format: ExportFormat.Value): Future[ByteString] = {
    val baos = new ByteArrayOutputStream()

    generate(uri).map { jasperPrint =>
      format match {
        case ExportFormat.pdf =>
          val exporter = new JRPdfExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.html =>
          val exporter = new HtmlExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleHtmlExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.docx =>
          val exporter = new JRDocxExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.xls =>
          val exporter = new JRXlsExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.pptx =>
          val exporter = new JRPptxExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.rtf =>
          val exporter = new JRRtfExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleWriterExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.odt =>
          val exporter = new JROdtExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.csv =>
          val exporter = new JRCsvExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleWriterExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.json =>
          val exporter = new JsonExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleJsonExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.xml =>
          val exporter = new JRXmlExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleXmlExporterOutput(baos))
          exporter.exportReport()
        case ExportFormat.text =>
          val exporter = new JRTextExporter(jsContext)
          val rptCfg = new SimpleTextReportConfiguration
          rptCfg.setPageHeightInChars(80)
          rptCfg.setPageWidthInChars(128)
          val exportCfg = new SimpleTextExporterConfiguration
          exportCfg.setLineSeparator("\n")
          exportCfg.setPageSeparator("-------------------------------------------------------------------------")
          exportCfg.setTrimLineRight(true)
          exporter.setConfiguration(exportCfg)
          exporter.setConfiguration(rptCfg)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleWriterExporterOutput(baos))
          exporter.exportReport()
      }

      ByteString(baos.toByteArray)
    }
  }

  def exportReportToFileSync(uri: String, format: ExportFormat.Value, destFilename: String)(implicit timeout: FiniteDuration): File = {
    val future = exportReportToFile(uri, format, destFilename)
    Await.result(future, timeout)
  }

  def exportReportToFile(uri: String, format: ExportFormat.Value, destFilename: String): Future[File] = {
    generate(uri).map { jasperPrint =>
      format match {
        case ExportFormat.pdf =>
          val exporter = new JRPdfExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.html =>
          val exporter = new HtmlExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleHtmlExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.docx =>
          val exporter = new JRDocxExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.xls =>
          val exporter = new JRXlsExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.pptx =>
          val exporter = new JRPptxExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.rtf =>
          val exporter = new JRRtfExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleWriterExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.odt =>
          val exporter = new JROdtExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.csv =>
          val exporter = new JRCsvExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleWriterExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.json =>
          val exporter = new JsonExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleJsonExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.xml =>
          val exporter = new JRXmlExporter(jsContext)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleXmlExporterOutput(destFilename))
          exporter.exportReport()
        case ExportFormat.text =>
          val exporter = new JRTextExporter(jsContext)
          val rptCfg = new SimpleTextReportConfiguration
          rptCfg.setPageHeightInChars(80)
          rptCfg.setPageWidthInChars(128)
          val exportCfg = new SimpleTextExporterConfiguration
          exportCfg.setLineSeparator("\n")
          exportCfg.setPageSeparator("-------------------------------------------------------------------------")
          exportCfg.setTrimLineRight(true)
          exporter.setConfiguration(exportCfg)
          exporter.setConfiguration(rptCfg)
          exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
          exporter.setExporterOutput(new SimpleWriterExporterOutput(destFilename))
          exporter.exportReport()
      }

      new File(destFilename)
    }
  }

  case class ReportUnitContents(jrxml: InputStream, resources: Map[String, InputStream], dataSource: Option[DataSourceResource])

  private def loadReportUnitContents(uri: String): Future[ReportUnitContents] = {
     backend.getResource(uri).map{
      case Left(ex) =>
        Future.failed(ex)
      case Right(r) if !r.isInstanceOf[ReportUnitResource] =>
        Future.failed(new RuntimeException(s"resource is not report unit, but ${r.resourceType}: $uri"))
      case Right(r) if r.isInstanceOf[ReportUnitResource] =>
        // ReportUnit loaded
        val ru = r.asInstanceOf[ReportUnitResource]

        // get jrxml file as an InputStream
        val jrxmlContent: Future[InputStream] = ru.jrxml match {
          case Some(fr) => backend.getContent(fr.uri).map {
            case Right(fc) =>
              val in = fc.source.runWith(StreamConverters.asInputStream(3.seconds))
              Future.successful(in)
            case Left(ex) =>
              Future.failed(ex)
          }.flatten
          case None => Future.failed(new RuntimeException("report unit doesn't contain jrxml"))
        }

        // get resource files as Map[name, InputStream]
        val resources: Future[Map[String, InputStream]] = Future.sequence(
          ru.resources map { case (resourceName, resource) =>
            backend.getContent(resource.uri).map {
              case Right(fc) =>
                val in = fc.source.runWith(StreamConverters.asInputStream(3.seconds))
                Future.successful((resourceName, in))
              case Left(ex) =>
                Future.failed(ex)
            }.flatten
          }).map(_.toSeq).map(seq => Map(seq:_*))

        // parallelize futures of reading contents
        for {
          jrxml <- jrxmlContent
          resourceMap <- resources
        } yield ReportUnitContents(jrxml, resourceMap, ru.dataSource)
    }.flatten
  }

  private def generate(uri: String): Future[JasperPrint] = {
    loadReportUnitContents(uri).map { ctnt =>
      val compiler = JasperCompileManager.getInstance(jsContext)

      ctnt.resources.foreach{ case (resourceName, in) =>
        jsContext.setValue(s"repo:$resourceName", in)
      }

      // 1. compile report from xml file
      val jsReport = compiler.compile(ctnt.jrxml)

      // 2. set parameters
      val params = new java.util.HashMap[String, AnyRef]()
      params.put("GREETING", "Hello-world (engine)")

      // 3. get database connection
      val dataSource = ctnt.dataSource match {
        case Some(x) if x.isInstanceOf[JdbcDataSourceResource] =>
          val ds = x.asInstanceOf[JdbcDataSourceResource]
          logger.debug(s"JDBC DS driver=${ds.driverClass} url=${ds.connectionUrl}")
          //TODO make data source instance
          new JREmptyDataSource
        case _ =>
          new JREmptyDataSource
      }

      // 4. create JasperPrint
      JasperFillManager.getInstance(jsContext).fill(jsReport, params, dataSource)
    }
  }
}

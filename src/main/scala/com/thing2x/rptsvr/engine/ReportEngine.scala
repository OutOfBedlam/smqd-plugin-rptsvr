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
import net.sf.jasperreports.engine.fonts.{FontExtensionsContainer, FontExtensionsRegistry, FontFamily, SimpleFontExtensionsRegistryFactory}
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

  private def compile(is: InputStream): Future[JasperReport] = Future {
    val compiler = JasperCompileManager.getInstance(jsContext)
    val report = compiler.compile(is)
    // save JasperReport to cache (java object serialization)
    //net.sf.jasperreports.engine.util.JRSaver.saveObject(report, new File("file_loc"))
    report
  }

  private def dataSource(dsResource: Option[DataSourceResource]): Future[JRDataSource] = Future {
    dsResource match {
      case Some(x) if x.isInstanceOf[JdbcDataSourceResource] =>
        val ds = x.asInstanceOf[JdbcDataSourceResource]
        logger.debug(s"JDBC DS driver=${ds.driverClass} url=${ds.connectionUrl}")
        //TODO make data source instance
        new JREmptyDataSource
      case _ =>
        new JREmptyDataSource
    }
  }

  private def generate(uri: String, parameters: Map[String, Any]): Future[JasperPrint] = {
    val compiled = for {
      ctnt <- loadReportUnitContents(uri)
      jsReport <- compile(ctnt.jrxml)
      ds <- dataSource(ctnt.dataSource)
    } yield (ctnt, jsReport, ds)

    compiled map { case (ctnt, jsReport, dataSource) =>
      // set parameters
      val params = new java.util.HashMap[String, AnyRef]()
      parameters.foreach { case (k, v) =>
        params.put(k, v.asInstanceOf[AnyRef])
      }

      // set resources
      ctnt.resources.foreach{ case (resourceName, in) =>
        jsContext.setValue(s"repo:$resourceName", in)
      }

      // create JasperPrint
      JasperFillManager.getInstance(jsContext).fill(jsReport, params, dataSource)
    }
  }
}

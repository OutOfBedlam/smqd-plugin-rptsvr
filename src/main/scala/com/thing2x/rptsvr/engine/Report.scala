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

import java.io.File

import akka.stream.Materializer
import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportEngine.ExportFormat
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine.{JasperFillManager, JasperPrint, JasperReport, SimpleJasperReportsContext}
import net.sf.jasperreports.repo.{PersistenceServiceFactory, RepositoryService}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class Report(engine: ReportEngine, val reportUnitUri: String)(implicit ec: ExecutionContext, mat: Materializer) extends StrictLogging {

  val jsContext = new SimpleJasperReportsContext()

  private val repositoryService = new EngineRepositoryService(jsContext, engine.backend)
  jsContext.setExtensions(classOf[RepositoryService], Seq(repositoryService).asJava)
  jsContext.setExtensions(classOf[PersistenceServiceFactory], Seq(EngineRepositoryPersistenceServiceFactory).asJava)

  def exportReport(parameters: Map[String, Any], format: ExportFormat.Value): Future[ByteString] = {
    val exporter = ReportExporter(jsContext, format)
    fill(parameters).map ( exporter.exportReport )
  }

  def exportReportToFile(parameters: Map[String, Any], format: ExportFormat.Value, destFilename: String, option:Option[String]): Future[File] = {
    val exporter = ReportExporter(jsContext, format, option)
    fill(parameters).map( exporter.exportReportToFile(_, destFilename))
  }

  def exportReportToFileSync(parameters: Map[String, Any], format: ExportFormat.Value, destFilename: String, option:Option[String] = None)(implicit timeout: FiniteDuration): File = {
    val future = exportReportToFile(parameters, format, destFilename, option)
    Await.result(future, timeout)
  }

  private var _compiledReportUnit: Option[Future[ReportUnitCompiled]] = None

  private def compile0: Future[ReportUnitCompiled] = synchronized {
    if (_compiledReportUnit.isEmpty) {
      val result = engine.loadReportUnit(this) map { cru =>
        // set resources
        cru.resources.foreach{ case (resourceName, in) =>
          jsContext.setValue(s"repo:$resourceName", in)
        }
        cru
      }
      _compiledReportUnit = Some(result)
    }
    _compiledReportUnit.get
  }

  def compile: Future[JasperReport] = {
    compile0.map(_.jsReport)
  }

  def fill(parameters: Map[String, Any]): Future[JasperPrint] = {
    val compiled = for {
      cru <- compile0
      ds <- engine.dataSource(cru.dataSource)
      jdbcConn <- engine.jdbcDataSource(cru.dataSource)
    } yield (cru.jsReport, ds, jdbcConn)

    compiled map { case (jsReport, dataSource, jdbcConnection) =>

      // set parameters
      val params = new java.util.HashMap[String, AnyRef]()
      parameters.foreach { case (k, v) =>
        params.put(k, v.asInstanceOf[AnyRef])
      }

      // create JasperPrint by filling JasperReport
      if (dataSource.isDefined) {
        JasperFillManager.getInstance(jsContext).fill(jsReport, params, dataSource.get)
      }
      else if (jdbcConnection.isDefined) {
        try {
          JasperFillManager.getInstance(jsContext).fill(jsReport, params, jdbcConnection.get)
        }
        finally {
          jdbcConnection.get.close()
        }
      }
      else {
        JasperFillManager.getInstance(jsContext).fill(jsReport, params)
      }
    }
  }
}

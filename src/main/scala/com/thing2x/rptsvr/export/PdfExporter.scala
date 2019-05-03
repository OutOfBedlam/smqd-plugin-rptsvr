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

package com.thing2x.rptsvr.export

import java.io.{ByteArrayOutputStream, File}

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine.export.{JRExportProgressMonitor, JRPdfExporter}
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext}
import net.sf.jasperreports.export._

class PdfExporter(jsContext: JasperReportsContext) extends ReportExporter with StrictLogging {

  override def exportReport(jasperPrint: JasperPrint): ByteString = {
    val baos = new ByteArrayOutputStream()
    val exporter = new JRPdfExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
    exporter.exportReport()
    ByteString(baos.toByteArray)
  }

  override def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File = {
    val exporter = new JRPdfExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destpath))

    exporter.setConfiguration(reportExportConfig.get)
    exporter.setConfiguration(exporterConfig.get)

    exporter.exportReport()
    new File(destpath)
  }

  def reportExportConfig: Option[PdfReportConfiguration] = {
    val cfg = new SimplePdfReportConfiguration
//    cfg.setProgressMonitor(this)
    Some(cfg)
  }

  def exporterConfig: Option[PdfExporterConfiguration] = {
    val cfg = new SimplePdfExporterConfiguration
    Some(cfg)
  }

//  implements JRExportProgressMonitor
//  override def afterPageExport(): Unit = {
//    logger.info("------------------------> progress")
//  }
}

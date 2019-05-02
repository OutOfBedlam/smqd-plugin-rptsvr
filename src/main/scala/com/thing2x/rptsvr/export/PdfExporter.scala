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

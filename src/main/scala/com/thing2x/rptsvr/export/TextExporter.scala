package com.thing2x.rptsvr.export

import java.io.{ByteArrayOutputStream, File}

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import net.sf.jasperreports.engine.export.JRTextExporter
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext}
import net.sf.jasperreports.export._

class TextExporter(jsContext: JasperReportsContext) extends ReportExporter {
  override def exportReport(jasperPrint: JasperPrint): ByteString = {
    val baos = new ByteArrayOutputStream()
    val exporter = new JRTextExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleWriterExporterOutput(baos))

    reportExportConfig.foreach( exporter.setConfiguration )
    exporterConfig.foreach( exporter.setConfiguration )

    exporter.exportReport()
    ByteString(baos.toByteArray)
  }

  override def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File = {
    val exporter = new JRTextExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleWriterExporterOutput(destpath))

    reportExportConfig.foreach( exporter.setConfiguration )
    exporterConfig.foreach( exporter.setConfiguration )

    exporter.exportReport()

    new File(destpath)
  }

  def reportExportConfig: Option[TextReportConfiguration] = {
    val cfg = new SimpleTextReportConfiguration
    cfg.setPageHeightInChars(80)
    cfg.setPageWidthInChars(128)
    Some(cfg)
  }

  def exporterConfig: Option[SimpleTextExporterConfiguration] = {
    val cfg = new SimpleTextExporterConfiguration
    cfg.setLineSeparator("\n")
    cfg.setPageSeparator("----------------------------------------------------------\n")
    cfg.setTrimLineRight(true)
    Some(cfg)
  }
}

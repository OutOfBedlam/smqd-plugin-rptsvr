package com.thing2x.rptsvr.export

import java.io.{ByteArrayOutputStream, File}

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext, export => underlying}
import net.sf.jasperreports.export.{SimpleExporterInput, SimpleJsonExporterOutput}

class JsonExporter(jsContext: JasperReportsContext) extends ReportExporter {
  override def exportReport(jasperPrint: JasperPrint): ByteString = {
    val baos = new ByteArrayOutputStream()
    val exporter = new underlying.JsonExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleJsonExporterOutput(baos))
    exporter.exportReport()
    ByteString(baos.toByteArray)
  }

  override def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File = {
    val exporter = new underlying.JsonExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleJsonExporterOutput(destpath))
    exporter.exportReport()
    new File(destpath)
  }
}

package com.thing2x.rptsvr.export

import java.io.{ByteArrayOutputStream, File}

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import net.sf.jasperreports.engine.export.JRXlsExporter
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext}
import net.sf.jasperreports.export.{SimpleExporterInput, SimpleOutputStreamExporterOutput}

class XlsExporter(jsContext: JasperReportsContext) extends ReportExporter {
  override def exportReport(jasperPrint: JasperPrint): ByteString = {
    val baos = new ByteArrayOutputStream()
    val exporter = new JRXlsExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos))
    exporter.exportReport()
    ByteString(baos.toByteArray)
  }

  override def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File = {
    val exporter = new JRXlsExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destpath))
    exporter.exportReport()
    new File(destpath)
  }
}
package com.thing2x.rptsvr.export

import java.io.{ByteArrayOutputStream, File}

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import net.sf.jasperreports.engine.export.JRXmlExporter
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext}
import net.sf.jasperreports.export.{SimpleExporterInput, SimpleXmlExporterOutput}

class XmlExporter(jsContext: JasperReportsContext) extends ReportExporter {
  override def exportReport(jasperPrint: JasperPrint): ByteString = {
    val baos = new ByteArrayOutputStream()
    val exporter = new JRXmlExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleXmlExporterOutput(baos))
    exporter.exportReport()
    ByteString(baos.toByteArray)
  }

  override def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File = {
    val exporter = new JRXmlExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleXmlExporterOutput(destpath))
    exporter.exportReport()
    new File(destpath)
  }
}

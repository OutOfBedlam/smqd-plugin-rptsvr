package com.thing2x.rptsvr.engine

import java.io.File

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportEngine.ExportFormat
import com.thing2x.rptsvr.export._
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext}

object ReportExporter {
  def apply(jsContext: JasperReportsContext, format: ExportFormat.Value): ReportExporter = {
    format match {
      case ExportFormat.pdf =>     new PdfExporter(jsContext)
      case ExportFormat.html =>    new HtmlExporter(jsContext)
      case ExportFormat.docx =>    new DocxExporter(jsContext)
      case ExportFormat.xls =>     new XlsExporter(jsContext)
      case ExportFormat.pptx =>    new PptxExporter(jsContext)
      case ExportFormat.rtf =>     new RtfExporter(jsContext)
      case ExportFormat.odt =>     new OdtExporter(jsContext)
      case ExportFormat.csv =>     new CsvExporter(jsContext)
      case ExportFormat.json =>    new JsonExporter(jsContext)
      case ExportFormat.xml =>     new XmlExporter(jsContext)
      case ExportFormat.text =>    new TextExporter(jsContext)
    }
  }
}

trait ReportExporter {
  def exportReport(jasperPrint: JasperPrint): ByteString
  def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File
}

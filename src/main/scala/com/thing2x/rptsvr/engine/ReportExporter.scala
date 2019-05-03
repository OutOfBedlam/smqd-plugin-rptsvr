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

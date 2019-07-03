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

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.Base64

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext, export => underlying}
import net.sf.jasperreports.export.SimpleExporterInput
import net.sf.jasperreports.engine.export.HtmlResourceHandler
import net.sf.jasperreports.export.SimpleHtmlExporterOutput
import net.sf.jasperreports.export.SimpleHtmlReportConfiguration

import scala.collection.mutable

class HtmlExporter(jsContext: JasperReportsContext) extends ReportExporter with StrictLogging {
  private val images = mutable.Map.empty[String,String]

  override def exportReport(jasperPrint: JasperPrint): ByteString = {
    val baos = new ByteArrayOutputStream()
    val exporter = new underlying.HtmlExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleHtmlExporterOutput(baos))
    exporter.exportReport()
    ByteString(baos.toByteArray)
  }

  override def exportReportToFile(jasperPrint: JasperPrint, destpath: String): File = {
    val exporter = new underlying.HtmlExporter(jsContext)
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint))
    exporter.setExporterOutput(new SimpleHtmlExporterOutput(destpath))

    val reportExportConfiguration = new SimpleHtmlReportConfiguration

    exporter.setConfiguration(reportExportConfiguration)

    val outputStream = new ByteArrayOutputStream()
    val simpleHtmlExporterOutput = new SimpleHtmlExporterOutput(outputStream)

    simpleHtmlExporterOutput.setImageHandler(new HtmlResourceHandler() {
      override def handleResource(id: String, data: Array[Byte]): Unit = {
        images.put(id, "data:image/jpg;base64," + Base64.getEncoder.encodeToString(data))
      }

      override def getResourcePath(id: String): String = images.getOrElse(id, "")
    })
    exporter.setExporterOutput(simpleHtmlExporterOutput)
    exporter.exportReport()

    val file = new File(destpath)
    val fos = new FileOutputStream( file)
    outputStream.writeTo(fos)
    fos.close()

    file
  }
}

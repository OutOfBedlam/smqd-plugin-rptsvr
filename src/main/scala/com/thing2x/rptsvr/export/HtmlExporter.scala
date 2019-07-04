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
import java.util.Base64
import java.io.PrintWriter

import akka.util.ByteString
import com.thing2x.rptsvr.engine.ReportExporter
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine.{JasperPrint, JasperReportsContext, export => underlying}
import net.sf.jasperreports.export.SimpleExporterInput
import net.sf.jasperreports.engine.export.HtmlResourceHandler
import net.sf.jasperreports.export.SimpleHtmlExporterOutput
import net.sf.jasperreports.export.SimpleHtmlReportConfiguration
import net.sf.jasperreports.web.util.WebHtmlResourceHandler

import scala.collection.mutable
import scala.util.matching.Regex

/**
  *
  * @param jsContext JasperContext
  * @param option
  *        "base64" : embed image data by base64 encoding
  *        "web:http://server/{0}" : convert the image source to the external link. {0} is replaced by the resource name
  *           if the 'isLazy' option is turned on, the image source is replaced by RegEx
  *             ex) url('repo:a.png') -> url('http://server/a.png')
  */
class HtmlExporter(jsContext: JasperReportsContext, option:Option[String]) extends ReportExporter with StrictLogging {
  private val images = mutable.Map.empty[String,String]
  private val imageUrl = new Regex( """url\('(repo:|\.\/)([^']+)'\)""", "ignore", "file")
  private val imageSrc = new Regex( """<img\s+src="(repo:|\.\/)([^"]+)"""", "ignore", "file")

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

    logger.info(s"html export option: $option, path=$destpath")

    option match {
      case None =>
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(destpath))
        exporter.exportReport()

      case Some(opt) =>

        val reportExportConfiguration = new SimpleHtmlReportConfiguration
        exporter.setConfiguration(reportExportConfiguration)

        val outputStream = new ByteArrayOutputStream()
        val simpleHtmlExporterOutput = new SimpleHtmlExporterOutput(outputStream)

        if ( opt == "base64") {
          simpleHtmlExporterOutput.setImageHandler(new HtmlResourceHandler() {
            override def handleResource(id: String, data: Array[Byte]): Unit = {
              logger.info(s"Image : $id ${jasperPrint.getProperty("image." + id)}")
              images.put(id, "data:image/jpg;base64," + Base64.getEncoder.encodeToString(data))
            }

            override def getResourcePath(id: String): String = images.getOrElse(id, "")
          })
        }
        else if ( opt.substring(0, 4) == "web:") {
          simpleHtmlExporterOutput.setImageHandler(new WebHtmlResourceHandler( opt.substring(4)))
        }

        exporter.setExporterOutput(simpleHtmlExporterOutput)
        exporter.exportReport()

        val pattern = opt.substring(4)
        val output1 = imageUrl.replaceAllIn( outputStream.toString(),
          m => s"""url('${pattern.replaceAllLiterally("{0}", m group "file")}')""")
        val output2 = imageSrc.replaceAllIn( output1,
          m => s"""<img src="${pattern.replaceAllLiterally("{0}", m group "file")}" """)

        val fos = new PrintWriter(destpath)
        fos.write(output2)
        fos.close()
    }

    new File(destpath)
  }
}

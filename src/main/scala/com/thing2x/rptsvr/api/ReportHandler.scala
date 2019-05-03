package com.thing2x.rptsvr.api

import akka.http.scaladsl.model._
import com.thing2x.rptsvr.engine.ReportEngine
import com.thing2x.rptsvr.engine.ReportEngine.ExportFormat
import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class ReportHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends StrictLogging  {

  private val engine = ReportEngine.findInstance(smqd)

  def getReport(uri: String, page: Option[Int], forceOctetStream: Option[Boolean]): Future[HttpResponse] = {
    val extPos = uri.lastIndexOf('.')
    if (extPos == -1 || extPos >= uri.length - 1) {
      Future( (StatusCodes.BadRequest, s"request should contains exporting format: $uri"))
    }
    else {
      val reportUri = uri.substring(0, extPos)
      val ext = uri.substring(extPos+1)
      val format = ExportFormat.valueOf(ext)

      if (format.isEmpty) {
        Future((StatusCodes.BadRequest, s"Invalid output format $ext"))
      }
      else {
        val report = engine.report(reportUri)
        report.exportReport(Map.empty, format.get).map { buff =>

          val contentType = if (forceOctetStream.getOrElse(false))
            ContentTypes.`application/octet-stream`
          else
            ExportFormat.contentType(format.get)

          HttpResponse(StatusCodes.OK, Nil, HttpEntity(contentType, buff))
        }
      }
    }
  }
}

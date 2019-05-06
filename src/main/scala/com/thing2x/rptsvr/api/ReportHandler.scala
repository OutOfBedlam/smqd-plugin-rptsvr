package com.thing2x.rptsvr.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.thing2x.rptsvr.engine.ReportEngine
import com.thing2x.rptsvr.engine.ReportEngine.ExportFormat
import com.thing2x.smqd.Smqd
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class ReportHandler(smqd: Smqd)(implicit executionContex: ExecutionContext) extends StrictLogging  {

  private val engine = ReportEngine.instance.get

  import smqd.Implicit._
  //private implicit val ec: ExecutionContext = smqd.Implicit

  val paramsUnmarshaller: Unmarshaller[String, Map[String, String]] = new Unmarshaller[String, Map[String, String]] {
    override def apply(value: String)(implicit ec: ExecutionContext, materializer: Materializer): Future[Map[String, String]] = Future {
      val seq = value.split(';').flatMap{ tok =>
        val idx = tok.indexOf('=')
        if (idx == -1 || idx >= tok.length - 1) {
          None
        }
        else {
          val k = tok.substring(0, idx)
          val v = tok.substring(idx+1)
          Some((k, v))
        }
      }.toSeq
      Map(seq:_*)
    }
  }

  def getReport(uri: String, parameters: Map[String, String], page: Option[Int], forceOctetStream: Boolean): Future[HttpResponse] = {
    val extPos = uri.lastIndexOf('.')
    if (extPos == -1 || extPos >= uri.length - 1) {
      Future( (StatusCodes.BadRequest, s"request missing parameter for exporting format: $uri"))
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
        report.exportReport(parameters, format.get).map { buff =>
          val contentType = if (forceOctetStream)
            ContentTypes.`application/octet-stream`
          else
            ExportFormat.contentType(format.get)

          HttpResponse(StatusCodes.OK, Nil, HttpEntity(contentType, buff))
        }
      }
    }
  }
}

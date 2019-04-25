package com.thing2x.rptsvr

import com.thing2x.rptsvr.ResourceHandler.{DSJdbcResource, FileResource, FolderResource, ReportUnitResource}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}

trait ResourceCodec {
  implicit val resourceEncoder: Encoder[Resource] = new Encoder[Resource] {
    override def apply(resource: Resource): Json = {
      resource match {
        case r: FolderResource => r.asJson
        case r: FileResource => r.asJson
        case r: DSJdbcResource => r.asJson
        case r: ReportUnitResource => r.asJson
      }
    }
  }
}

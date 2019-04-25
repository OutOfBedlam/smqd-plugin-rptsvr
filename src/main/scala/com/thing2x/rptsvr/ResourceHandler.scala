package com.thing2x.rptsvr

import com.thing2x.rptsvr.ResourceHandler.Resource
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

object ResourceHandler {
  case class Resource(uri: String,
                      label: String,
                      description: String,
                      `type`: String,
                      permissionMask: Int,
                      creationDate: String,
                      updateDate: String,
                      version: Int
                     )
}

class ResourceHandler()(implicit executionContex: ExecutionContext) extends StrictLogging {
  def getResources(path: String, expanded: Boolean): Future[Seq[Resource]] = Future {
    logger.debug(s"path=$path expanded=$expanded")
    Seq(Resource(uri=path, label="Sample Label", description="Sample description", `type`="folder",
      permissionMask=0, creationDate="2013-07-04T12:18:47", updateDate="2013-07-04T12:18:47", version=0))
  }
}

package com.thing2x.rptsvr

import scala.concurrent.Future

trait Repository {
  def listFolder(folderUri: String, recursive: Boolean, sortBy: String, limit: Int): Future[Seq[Resource]]
  def getResource(path: String, expanded: Boolean): Future[Resource]
}

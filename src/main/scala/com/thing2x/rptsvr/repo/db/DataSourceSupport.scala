package com.thing2x.rptsvr.repo.db

import scala.concurrent.Future

trait DataSourceSupport { myself: DBRepository =>
  def selectDataSource(path: String): Future[JIResourceObject] = ???
}

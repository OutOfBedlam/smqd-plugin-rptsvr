package com.thing2x.rptsvr

import java.text.SimpleDateFormat

import scala.concurrent.ExecutionContext

trait RepositoryContext {
  val dateFormat: SimpleDateFormat
  val datetimeFormat: SimpleDateFormat
  val repository: Repository
  val executionContext: ExecutionContext
}


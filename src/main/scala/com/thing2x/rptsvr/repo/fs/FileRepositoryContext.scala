package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat

import com.thing2x.rptsvr.{Repository, RepositoryContext}

import scala.concurrent.ExecutionContext

class FileRepositoryContext(val repository: Repository, val executionContext: ExecutionContext, val root: File, val dateFormat: SimpleDateFormat, val datetimeFormat: SimpleDateFormat) extends RepositoryContext{

}

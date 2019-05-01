package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat

import akka.stream.Materializer
import com.thing2x.rptsvr.{Repository, RepositoryContext}

import scala.concurrent.ExecutionContext

class FileRepositoryContext(
                             val repository: Repository,
                             val executionContext: ExecutionContext,
                             val materializer: Materializer,
                             val root: File,
                             val dateFormat: SimpleDateFormat,
                             val datetimeFormat: SimpleDateFormat) extends RepositoryContext

package com.thing2x.rptsvr.repo.fs

import java.io.File
import java.text.SimpleDateFormat

import com.thing2x.rptsvr.RepositoryContext

class FileRepositoryContext(val root: File, val dateFormat: SimpleDateFormat, val datetimeFormat: SimpleDateFormat) extends RepositoryContext{

}

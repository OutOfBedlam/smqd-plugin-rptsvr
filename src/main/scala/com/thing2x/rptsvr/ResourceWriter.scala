package com.thing2x.rptsvr

trait ResourceWriter {
  def writeMeta(resource: Resource): Unit
  def writeContent(base64Content: String): Unit
}

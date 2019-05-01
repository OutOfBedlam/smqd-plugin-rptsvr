package com.thing2x.rptsvr.engine

import java.io.{InputStream, OutputStream}

import com.thing2x.rptsvr.{Repository => BackendRepo}
import net.sf.jasperreports.engine.JasperReportsContext
import net.sf.jasperreports.repo._

class EngineRepositoryService(jsContext: JasperReportsContext, backend: BackendRepo) extends StreamRepositoryService {
  override def getInputStream(uri: String): InputStream = ???

  override def getOutputStream(uri: String): OutputStream = ???

  override def getResource(uri: String): Resource = ???

  override def saveResource(uri: String, resource: Resource): Unit = ???

  override def getResource[K <: Resource](uri: String, resourceType: Class[K]): K = {
    val repositoryContext = SimpleRepositoryContext.of(jsContext)
    val persistenceService = PersistenceUtil.getInstance(jsContext).getService(classOf[EngineRepositoryService], resourceType)
    if (persistenceService != null)
    {
      println(s"********************** $uri")
      val is = repositoryContext.getJasperReportsContext.getValue(uri).asInstanceOf[InputStream]
      if (is != null) {
        val isr = new InputStreamResource
        isr.setInputStream(is)
        return isr.asInstanceOf[K]
      }
    }

    null.asInstanceOf[K]
  }
}

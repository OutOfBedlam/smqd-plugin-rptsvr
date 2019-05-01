package com.thing2x.rptsvr.engine

import java.io.{InputStream, OutputStream}

import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import com.thing2x.rptsvr.{Repository => BackendRepo}
import net.sf.jasperreports.engine.JasperReportsContext
import net.sf.jasperreports.repo.{PersistenceUtil, Resource, SimpleRepositoryContext, StreamRepositoryService}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class EngineRepositoryService(jsContext: JasperReportsContext, backend: BackendRepo) extends StreamRepositoryService {
  override def getInputStream(uri: String): InputStream = {
    implicit val ec: ExecutionContext = backend.context.executionContext
    implicit val materializer: Materializer = backend.context.materializer

    val future = backend.getContent(uri).map {
      case Right(result) => result.source.runWith(StreamConverters.asInputStream(3.seconds))
      case _ => null
    }
    Await.result(future, 3.seconds)
  }

  override def getOutputStream(uri: String): OutputStream = ???

  override def getResource(uri: String): Resource = ???

  override def saveResource(uri: String, resource: Resource): Unit = ???

  override def getResource[K <: Resource](uri: String, resourceType: Class[K]): K = {
    val repositoryContext = SimpleRepositoryContext.of(jsContext)
    val persistenceService = PersistenceUtil.getInstance(jsContext).getService(classOf[EngineRepositoryService], resourceType)
    if (persistenceService != null)
    {
      persistenceService.load(repositoryContext, uri, this).asInstanceOf[K]
    }
    else{
      null.asInstanceOf[K]
    }
  }
}

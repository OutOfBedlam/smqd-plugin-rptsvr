package com.thing2x.rptsvr.engine

import java.io.ByteArrayOutputStream

import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import com.thing2x.rptsvr.{FileContent, FileResource, ReportUnitResource, Repository}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine.{JREmptyDataSource, JasperCompileManager, JasperExportManager, JasperFillManager, SimpleJasperReportsContext}
import net.sf.jasperreports.repo.{PersistenceServiceFactory, RepositoryService}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object Engine {
  def findInstance(smqd: Smqd): Engine = {
    val engineClass = classOf[Engine]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      engineClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[Engine]).get
  }
}

class Engine(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  private val jsContext = new SimpleJasperReportsContext()
  private val backend = Repository.findInstance(smqd)

  override def start(): Unit = {
    val repositoryService = new EngineRepositoryService(jsContext, backend)
    jsContext.setExtensions(classOf[RepositoryService], Seq(repositoryService).asJava)
    jsContext.setExtensions(classOf[PersistenceServiceFactory], Seq(EngineRepositoryPersistenceServiceFactory).asJava)
  }

  override def stop(): Unit = {

  }

  def generate(path: String = "/unit_test_folder/rptunit"): Unit = {

    implicit val ec: ExecutionContext = backend.context.executionContext
    implicit val materializer: Materializer = backend.context.materializer

    val jrxmlSource = backend.getResource(path).map[Either[Throwable, FileResource]]{
      case Right(r) if r.isInstanceOf[ReportUnitResource] =>
        val ru = r.asInstanceOf[ReportUnitResource]
        if (ru.jrxml.isDefined)
          Right(ru.jrxml.get)
        else
          Left(new RuntimeException("report unit doesn't contain jrxml"))
      case Right(r) =>
        Left(new RuntimeException(s"resource is not report unit, but ${r.resourceType}: $path"))
      case _ =>
        Left(new RuntimeException(s"path not found: $path"))
    } flatMap[Either[Throwable, FileContent]] {
      case Right(jrxml) =>
        backend.getContent(jrxml.uri)
      case Left(ex) =>
        Future(Left(ex))
    }

    Await.result(jrxmlSource, 3.seconds) match {
      case Left(ex) =>
        logger.error("report compile fail", ex)
      case Right(content) =>
        val is = content.source.runWith(StreamConverters.asInputStream(3.seconds))
        val compiler = JasperCompileManager.getInstance(jsContext)

        //val os = new ByteArrayOutputStream
        // 1. compile report from xml file
        val jsReport = compiler.compile(is)

        // 2. set parameters
        val params = new java.util.HashMap[String, AnyRef]()
        params.put("GREETING", "Hello-world (engine)")

        // 3. get database connection
        val ds = new JREmptyDataSource

        // 4. create JasperPrint
        val jasperPrint = JasperFillManager.getInstance(jsContext).fill(jsReport, params, ds)

        // 5. create pdf
        JasperExportManager.getInstance(jsContext).exportToPdfFile(jasperPrint, "./src/test/test_result.pdf")
    }
  }
}

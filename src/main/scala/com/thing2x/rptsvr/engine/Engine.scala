package com.thing2x.rptsvr.engine

import java.io.{ByteArrayOutputStream, InputStream}

import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import com.thing2x.rptsvr.{DataSourceResource, FileContent, FileResource, JdbcDataSourceResource, ReportUnitResource, Repository, Result}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.sf.jasperreports.engine.{JREmptyDataSource, JasperCompileManager, JasperExportManager, JasperFillManager, SimpleJasperReportsContext}
import net.sf.jasperreports.repo.{PersistenceServiceFactory, RepositoryService}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Engine {
  def findInstance(smqd: Smqd): Engine = {
    val engineClass = classOf[Engine]
    smqd.pluginManager.pluginDefinitions.find{ pd =>
      engineClass.isAssignableFrom(pd.clazz)
    }.map(_.instances.head.instance.asInstanceOf[Engine]).get
  }
}

class Engine(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging {

  private val backend = Repository.findInstance(smqd)

  override def start(): Unit = {
  }

  override def stop(): Unit = {

  }

  def generate(path: String = "/unit_test_folder/rptunit"): Unit = {

    implicit val ec: ExecutionContext = backend.context.executionContext
    implicit val materializer: Materializer = backend.context.materializer

    val jsContext = new SimpleJasperReportsContext()
    val repositoryService = new EngineRepositoryService(jsContext, backend)
    jsContext.setExtensions(classOf[RepositoryService], Seq(repositoryService).asJava)
    jsContext.setExtensions(classOf[PersistenceServiceFactory], Seq(EngineRepositoryPersistenceServiceFactory).asJava)

    case class ReportUnitContents(jrxml: InputStream, resources: Map[String, InputStream], dataSource: Option[DataSourceResource])

    val reportContents = backend.getResource(path).map{
      case Left(ex) =>
        Future.failed(ex)
      case Right(r) if !r.isInstanceOf[ReportUnitResource] =>
        Future.failed(new RuntimeException(s"resource is not report unit, but ${r.resourceType}: $path"))
      case Right(r) if r.isInstanceOf[ReportUnitResource] =>
        val ru = r.asInstanceOf[ReportUnitResource]

        val jrxmlContent: Future[InputStream] = ru.jrxml match {
          case Some(fr) => backend.getContent(fr.uri).map {
            case Right(fc) =>
              val in = fc.source.runWith(StreamConverters.asInputStream(3.seconds))
              Future.successful(in)
            case Left(ex) =>
              Future.failed(ex)
          }.flatten
          case None => Future.failed(new RuntimeException("report unit doesn't contain jrxml"))
        }

        val resources: Future[Map[String, InputStream]] = Future.sequence(
          ru.resources map { case (resourceName, resource) =>
            backend.getContent(resource.uri).map {
              case Right(fc) =>
                val in = fc.source.runWith(StreamConverters.asInputStream(3.seconds))
                Future.successful((resourceName, in))
              case Left(ex) =>
                Future.failed(ex)
            }.flatten
          }).map(_.toSeq).map(seq => Map(seq:_*))

        // parallel reading contents
        for {
          jrxml <- jrxmlContent
          resourceMap <- resources
        } yield ReportUnitContents(jrxml, resourceMap, ru.dataSource)
    }.flatten

    val doneFlag = Promise[Boolean]

    reportContents.onComplete {
      case Failure(ex) =>
        logger.error("report compile fail", ex)
      case Success(ctnt) =>
        val compiler = JasperCompileManager.getInstance(jsContext)

        ctnt.resources.foreach{ case (resourceName, in) =>
          jsContext.setValue(s"repo:$resourceName", in)
        }

        //val os = new ByteArrayOutputStream
        // 1. compile report from xml file
        val jsReport = compiler.compile(ctnt.jrxml)

        // 2. set parameters
        val params = new java.util.HashMap[String, AnyRef]()
        params.put("GREETING", "Hello-world (engine)")

        // 3. get database connection
        val dataSource = ctnt.dataSource match {
          case Some(x) if x.isInstanceOf[JdbcDataSourceResource] =>
            val ds = x.asInstanceOf[JdbcDataSourceResource]
            logger.debug(s"JDBC DS driver=${ds.driverClass} url=${ds.connectionUrl}")
            //TODO make data source instance
            new JREmptyDataSource
          case _ =>
            new JREmptyDataSource
        }

        // 4. create JasperPrint
        val jasperPrint = JasperFillManager.getInstance(jsContext).fill(jsReport, params, dataSource)

        // 5. create pdf
        JasperExportManager.getInstance(jsContext).exportToPdfFile(jasperPrint, "./src/test/test_result.pdf")

        doneFlag.success(true)
    }

    Await.result(doneFlag.future, 8.seconds)

//    Thread.sleep(5000)
  }
}

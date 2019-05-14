package com.thing2x.rptsvr.repo.db

import java.sql.Driver
import java.text.SimpleDateFormat

import akka.stream.Materializer
import com.thing2x.rptsvr.{Repository, RepositoryContext}
import com.thing2x.smqd.Smqd
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DBRepositoryContext(val repository: Repository, val smqd: Smqd, config: Config) extends RepositoryContext {

  val readOnly: Boolean = config.getBoolean("readonly")
  val dateFormat: SimpleDateFormat = new SimpleDateFormat(config.getString("formats.date"))
  val datetimeFormat: SimpleDateFormat = new SimpleDateFormat(config.getString("formats.datetime"))
  val executionContext: ExecutionContext = smqd.Implicit.gloablDispatcher
  val materializer: Materializer = smqd.Implicit.materializer

  private val deferedBlock = new ListBuffer[() => Unit]

  import DBSchema.profile.api._

  private var _database: Database = _
  def database: Database = _database

  def open()(block: => Unit): Unit = {
    val clazz = Class.forName("org.h2.Driver")
    val driver = clazz.getDeclaredConstructor().newInstance().asInstanceOf[Driver]

    val url = "jdbc:h2:tcp://localhost:9099/mem:sampledb"
    _database = Database.forDriver(driver, url, "sa", "sa")

    defer(block)
  }

  def close(): Unit = {
    for (block <- deferedBlock) block()
    _database.close()
  }

  def defer(block: => Unit): Unit = {
    deferedBlock += ( () => block )
  }

  private[db] def run[T](act: DBIO[T]): Future[T] = _database.run(act)

  private[db] def runSync[T](act: DBIO[T], timeout: Duration): T = Await.result(_database.run(act), timeout)
}

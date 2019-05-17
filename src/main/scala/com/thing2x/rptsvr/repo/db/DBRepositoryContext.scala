// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.thing2x.rptsvr.repo.db

import java.sql.Driver
import java.text.SimpleDateFormat

import akka.stream.Materializer
import com.thing2x.rptsvr.{Repository, RepositoryContext}
import com.thing2x.smqd.Smqd
import com.typesafe.config.Config
import slick.jdbc.JdbcProfile

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DBRepositoryContext(val repository: Repository, val smqd: Smqd, config: Config) extends RepositoryContext {

  val profile: JdbcProfile =
  {
    config.getString("jdbc.driver") match {
      case "org.h2.Driver" => slick.jdbc.H2Profile
    }
  }

  val readOnly: Boolean = config.getBoolean("readonly")
  val dateFormat: SimpleDateFormat = new SimpleDateFormat(config.getString("formats.date"))
  val datetimeFormat: SimpleDateFormat = new SimpleDateFormat(config.getString("formats.datetime"))
  val executionContext: ExecutionContext = smqd.Implicit.gloablDispatcher
  val materializer: Materializer = smqd.Implicit.materializer

  private val deferedBlock = new ListBuffer[() => Unit]

  import profile.api._

  private var _database: Database = _
  def database: Database = _database

  def open()(block: => Unit): Unit = {
    val driverClass = config.getString("jdbc.driver")
    val connectionUrl = config.getString("jdbc.url")
    val username = config.getString("jdbc.username")
    val password = config.getString("jdbc.password")

    val clazz = Class.forName(driverClass)
    val driver = clazz.getDeclaredConstructor().newInstance().asInstanceOf[Driver]

    _database = Database.forDriver(driver, connectionUrl, username, password)

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

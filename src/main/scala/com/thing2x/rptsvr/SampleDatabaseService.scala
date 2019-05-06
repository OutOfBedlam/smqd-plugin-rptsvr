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

package com.thing2x.rptsvr

import com.thing2x.rptsvr.repo.db.DBRepository
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.thing2x.smqd.util.ConfigUtil._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

class SampleDatabaseService(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging  {

  private val initTcpPort = config.getOptionInt("tcp.port").getOrElse(0)

  // H2Database setup
  private val database: Database = Database.forConfig("sampledb", ConfigFactory.parseString(
    s"""
       |sampledb {
       |  driver = "org.h2.Driver"
       |  url    = "jdbc:h2:mem:sampledb;mode=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;IFEXISTS=FALSE"
       |  user   = "sa"
       |  password = "sa"
       |  keepAliveConnection = true
       |  connectionPool = disabled
       |}
      """.stripMargin))

  private val tcpServer = org.h2.tools.Server.createTcpServer(
    Array("-tcpPort", initTcpPort.toString, "-tcpAllowOthers", "-tcpPassword", "sa", "-tcpDaemon"):_*)

  def tcpPort: Int = tcpServer.getPort

  override def start(): Unit = {
    //////////////////////////////////////
    // sample_table
    //////////////////////////////////////
    val sampleUsers = TableQuery[SampleUserTable]

    import smqd.Implicit.gloablDispatcher

    // create table sample_table
    val setup = for {
      _ <- sampleUsers.schema.create
      rowsAdded <- sampleUsers ++= Seq(
        SampleUser("Smith", 100.1, "smith@email.com"),
        SampleUser("Marry", 19.9, "marry@email.com"),
        SampleUser("John", 32.5, "john@email.com"),
        SampleUser("Steve", 51.2, "steve@email.com"),
        SampleUser("Robert", 82.3, "robert@email.com"),
      )
    } yield rowsAdded

    Await.result(database.run(setup), 2.second)

    // start tcp connector
    tcpServer.start()
    logger.info(s"SampleDatabase service: ${tcpServer.getStatus}")
  }

  override def stop(): Unit = {
    tcpServer.stop()
    database.close()
  }
}

final case class SampleUser(name: String, cost: Double, email: String)

final class SampleUserTable(tag: Tag) extends Table[SampleUser](tag, "sample_table") {
  def name = column[String]("name", O.PrimaryKey)
  def cost = column[Double]("cost")
  def email = column[String]("email")
  def * = (name, cost, email).mapTo[SampleUser]
}
package com.thing2x.rptsvr

import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.thing2x.smqd.util.ConfigUtil._
import org.h2.jdbcx.JdbcDataSource

class SampleDatabaseService(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with StrictLogging  {

  private val initTcpPort = config.getOptionInt("tcp.port").getOrElse(0)

  private val tcpServer = org.h2.tools.Server.createTcpServer(
    Array("-tcpPort", initTcpPort.toString, "-tcpAllowOthers", "-tcpPassword", "sa", "-tcpDaemon"):_*)

  def tcpPort: Int = tcpServer.getPort

  override def start(): Unit = {
    tcpServer.start()

    logger.info(s"SampleDatabase service: ${tcpServer.getStatus}")
    // H2Database setup
    val ds = new JdbcDataSource
    ds.setURL(s"jdbc:h2:mem:sampledb;mode=MySQL;DB_CLOSE_DELAY=-1")
    ds.setUser("sa")
    ds.setPassword("sa")

    // create table and insert test data
    val conn = ds.getConnection
    val stmt1 = conn.createStatement()
    stmt1.executeUpdate(
      """
        |create table if not exists sample_table(
        |   NAME VARCHAR(80),
        |   COST NUMBER,
        |   EMAIL VARCHAR(40)
        |)
      """.stripMargin)

    val data = Seq(
      ("Smith", 100.1, "smith@email.com"),
      ("Marry", 19.9, "marry@email.com"),
      ("John", 32.5, "john@email.com"),
    )
    data.foreach{ r =>
      val stmt = conn.prepareStatement("INSERT INTO sample_table VALUES(?, ?, ?)")
      stmt.setString(1, r._1)
      stmt.setDouble(2, r._2)
      stmt.setString(3, r._3)
      stmt.executeUpdate()
    }
    conn.close()

  }

  override def stop(): Unit = {
    tcpServer.stop()
  }
}

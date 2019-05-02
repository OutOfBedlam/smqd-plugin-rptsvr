package com.thing2x.rptsvr.api

import org.h2.jdbcx.JdbcDataSource

class SampleDatabase(_port: Int = 0) {

  val args: Array[String] = Array("-tcp", "-tcpPort", _port.toString, "-tcpAllowOthers", "-tcpPassword", "sa")
  private val server = org.h2.tools.Server.createTcpServer(args:_*)
  server.start()

  // H2Database setup
  val ds = new JdbcDataSource
  ds.setURL(s"jdbc:h2:mem:sampledb;mode=MySQL;DB_CLOSE_DELAY=-1")
  ds.setUser("sa")
  ds.setPassword("sa")

  // create table and insert test data
  private val conn = ds.getConnection
  private val stmt1 = conn.createStatement()
  stmt1.executeUpdate(
    """
      |create table if not exists sample_table(
      |   NAME VARCHAR(80),
      |   COST NUMBER,
      |   EMAIL VARCHAR(40)
      |)
    """.stripMargin)

  private val data = Seq(
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

  val port: Int = server.getPort

  def stop(): Unit = {
    server.stop()
  }
}

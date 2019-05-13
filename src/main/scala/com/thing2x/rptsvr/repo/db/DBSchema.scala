package com.thing2x.rptsvr.repo.db

import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

object DBSchema {
  val resources = TableQuery[JIResourceTable]

  val resourceFolders = TableQuery[JIResourceFolderTable]

  val fileResources = TableQuery[JIFileResourceTable]
  val jdbcResources = TableQuery[JIJdbcDatasourceTable]
  val queryResources = TableQuery[JIQueryTable]

  val reportUnits = TableQuery[JIReportUnitTable]

  val reportUnitResources = TableQuery[JIReportUnitResourceTable]
  val reportUnitInputControls = TableQuery[JIReportUnitInputControlTable]

  val dataTypes = TableQuery[JIDataTypeTable]
  val inputControls = TableQuery[JIInputControlTable]
  val inputControlQueryColumns = TableQuery[JIInputControlQueryColumnTable]
  val listOfValues = TableQuery[JIListOfValuesTable]

  val schema: Seq[H2Profile.DDL] = Seq(
    resourceFolders.schema,
    resources.schema,
    fileResources.schema,
    jdbcResources.schema,
    queryResources.schema,
    dataTypes.schema,
    listOfValues.schema,
    inputControls.schema,
    inputControlQueryColumns.schema,
    reportUnits.schema,
    reportUnitResources.schema,
    reportUnitInputControls.schema,
  )

  def createSchema(ctx: DBRepositoryContext): Unit = {
    val resourceFolderInsert = resourceFolders returning resourceFolders.map(_.id)
    implicit val ec: ExecutionContext = ctx.executionContext
    val setup = for {
      // create tables
      _                <- schema.reduce( _ ++ _).create
      // insert default tables
      rootFolderId     <- resourceFolderInsert += JIResourceFolder("/", "/", "/", Some("Root"), -1)
      orgId            <- resourceFolderInsert += JIResourceFolder("/organizations", "organizations", "Organizations", Some("Oraganizations"), rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/public", "public", "Public", None, rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/temp", "temp", "Temp", Some("Temp"), rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/organizations/organization_1", "organization_1", "Organization", None, orgId)
    } yield rootFolderId

    ctx.runSync(setup, 8.second)
  }

}

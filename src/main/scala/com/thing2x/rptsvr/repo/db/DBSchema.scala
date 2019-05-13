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

  val schema: Seq[H2Profile.DDL] = Seq(
    resourceFolders.schema,
    resources.schema,
    fileResources.schema,
    jdbcResources.schema,
    queryResources.schema,
    dataTypes.schema,
    inputControls.schema,
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

//////////////////////////////////////////
// JIDataModelKind
//////////////////////////////////////////

trait JIDataModelKind

// create table JIObjectPermission (
//        id number(19,0) not null,
//        uri nvarchar2(1000) not null,
//        recipientobjectclass nvarchar2(250),
//        recipientobjectid number(19,0),
//        permissionMask number(10,0) not null,
//        primary key (id)
//    );



//     create table JITenant (
//        id number(19,0) not null,
//        tenantId nvarchar2(100) not null,
//        tenantAlias nvarchar2(100) not null,
//        parentId number(19,0),
//        tenantName nvarchar2(100) not null,
//        tenantDesc nvarchar2(250),
//        tenantNote nvarchar2(250),
//        tenantUri nvarchar2(250) not null,
//        tenantFolderUri nvarchar2(250) not null,
//        theme nvarchar2(250),
//        primary key (id),
//        unique (tenantId)
//    );
//
//    create table JIUser (
//        id number(19,0) not null,
//        username nvarchar2(100) not null,
//        tenantId number(19,0) not null,
//        fullname nvarchar2(100) not null,
//        emailAddress nvarchar2(100),
//        password nvarchar2(250),
//        externallyDefined number(1,0),
//        enabled number(1,0),
//        previousPasswordChangeTime date,
//        primary key (id),
//        unique (username, tenantId)
//    );
//
//    create table JIUserRole (
//        roleId number(19,0) not null,
//        userId number(19,0) not null,
//        primary key (userId, roleId)
//    );


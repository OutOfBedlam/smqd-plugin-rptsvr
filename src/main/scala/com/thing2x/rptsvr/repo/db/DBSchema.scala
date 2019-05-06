package com.thing2x.rptsvr.repo.db

import java.sql.{Blob, Date}

import com.thing2x.rptsvr.repo.db.DBRepository._
import slick.lifted.ProvenShape
import slick.jdbc.H2Profile.api._

//     create table JIResource (
//        id number(19,0) not null,
//        version number(10,0) not null,
//        name nvarchar2(200) not null,
//        parent_folder number(19,0) not null,
//        childrenFolder number(19,0),
//        label nvarchar2(200) not null,
//        description nvarchar2(250),
//        resourceType nvarchar2(255) not null,
//        creation_date date not null,
//        update_date date not null,
//        primary key (id),
//        unique (name, parent_folder)
//    );
final case class JIResource( name: String,
                             parentFolder: Long,
                             childrenFolder: Long,
                             label: String,
                             descriptoin: Option[String],
                             resourceType: String,
                             creationDate: Date = new Date(System.currentTimeMillis),
                             updateDate: Date = new Date(System.currentTimeMillis),
                             version: Long = -1,
                             id: Long = 0L)

final class JIResourceTable(tag: Tag) extends Table[JIResource](tag, "JIResource") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def version = column[Long]("version")
  def name = column[String]("name")
  def parentFolder = column[Long]("parent_folder")
  def childrenFolder = column[Long]("childrenFolder")
  def label = column[String]("label")
  def description = column[Option[String]]("description")
  def resourceType = column[String]("resourceType")
  def creationDate = column[Date]("creation_date")
  def updateDate = column[Date]("update_date")

  def folder = foreignKey("parent_folder_fk", parentFolder, resourceFolders)(_.id)

  def * = (name, parentFolder, childrenFolder, label, description, resourceType, creationDate, updateDate, version, id).mapTo[JIResource]
}

//    create table JIResourceFolder (
//        id number(19,0) not null,
//        version number(10,0) not null,
//        uri nvarchar2(250) not null,
//        hidden number(1,0),
//        name nvarchar2(200) not null,
//        label nvarchar2(200) not null,
//        description nvarchar2(250),
//        parent_folder number(19,0),
//        creation_date date not null,
//        update_date date not null,
//        primary key (id),
//        unique (uri)
//    );
final case class JIResourceFolder( uri: String,
                                   name: String,
                                   label: String,
                                   description: Option[String],
                                   parentFolder: Long,
                                   hidden: Boolean = false,
                                   creationDate: Date = new Date(System.currentTimeMillis),
                                   updateDate: Date = new Date(System.currentTimeMillis),
                                   version: Long = -1,
                                   id: Long = 0L)

final class JIResourceFolderTable(tag: Tag) extends Table[JIResourceFolder](tag, "JIResourceFolder") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def version = column[Long]("version")
  def uri = column[String]("uri", O.Unique)
  def hidden = column[Boolean]("hidden", O.Default(false))
  def name = column[String]("name")
  def label = column[String]("label")
  def description = column[Option[String]]("description")
  def parentFolder = column[Long]("parent_folder")
  def creationDate = column[Date]("creation_date")
  def updateDate = column[Date]("update_date")

  def * = (uri, name, label, description, parentFolder, hidden, creationDate, updateDate, version, id).mapTo[JIResourceFolder]
}

//    create table JIFileResource (
//        id number(19,0) not null,
//        data blob,
//        file_type nvarchar2(20),
//        reference number(19,0),
//        primary key (id)
//    );
final case class JIFileResource( fileType: String,
                                 data: Option[Blob],
                                 reference: Long,
                                 id: Long = 0L)

final class JIFileResourceTable(tag: Tag) extends Table[JIFileResource](tag, "JIFileResource") {
  def fileType    = column[String]("file_type")
  def data   = column[Option[Blob]]("data")
  def reference    = column[Long]("reference")
  def id           = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def * : ProvenShape[JIFileResource] = (fileType, data, reference, id).mapTo[JIFileResource]
}

//    create table JIContentResource (
//        id number(19,0) not null,
//        data blob,
//        file_type nvarchar2(20),
//        primary key (id)
//    );
final case class JIContentResource( serviceClass: String,
                                    data: Option[Blob],
                                    id: Long = 0L)

final class JIContentResourceTable(tag: Tag) extends Table[JIContentResource](tag, "JIContentResource") {
  def serviceClass = column[String]("serviceClass")
  def data = column[Option[Blob]]("data")
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def * = (serviceClass, data, id).mapTo[JIContentResource]
}

//    create table JIDataType (
//        id number(19,0) not null,
//        type number(3,0),
//        maxLength number(10,0),
//        decimals number(10,0),
//        regularExpr nvarchar2(255),
//        minValue raw(1000),
//        max_value raw(1000),
//        strictMin number(1,0),
//        strictMax number(1,0),
//        primary key (id)
//    );


// create table JIInputControl (
//        id number(19,0) not null,
//        type number(3,0),
//        mandatory number(1,0),
//        readOnly number(1,0),
//        visible number(1,0),
//        data_type number(19,0),
//        list_of_values number(19,0),
//        list_query number(19,0),
//        query_value_column nvarchar2(200),
//        defaultValue raw(255),
//        primary key (id)
//    );


// create table JIInputControlQueryColumn (
//        input_control_id number(19,0) not null,
//        query_column nvarchar2(200) not null,
//        column_index number(10,0) not null,
//        primary key (input_control_id, column_index)
//    );

// create table JIJdbcDatasource (
//        id number(19,0) not null,
//        driver nvarchar2(100) not null,
//        password nvarchar2(250),
//        connectionUrl nvarchar2(500),
//        username nvarchar2(100),
//        timezone nvarchar2(100),
//        primary key (id)
//    );

// create table JIObjectPermission (
//        id number(19,0) not null,
//        uri nvarchar2(1000) not null,
//        recipientobjectclass nvarchar2(250),
//        recipientobjectid number(19,0),
//        permissionMask number(10,0) not null,
//        primary key (id)
//    );

// create table JIQuery (
//        id number(19,0) not null,
//        dataSource number(19,0),
//        query_language nvarchar2(40) not null,
//        sql_query nclob not null,
//        primary key (id)
//    );


// create table JIReportUnit (
//        id number(19,0) not null,
//        reportDataSource number(19,0),
//        query number(19,0),
//        mainReport number(19,0),
//        controlrenderer nvarchar2(100),
//        reportrenderer nvarchar2(100),
//        promptcontrols number(1,0),
//        controlslayout number(3,0),
//        data_snapshot_id number(19,0),
//        primary key (id)
//    );

//    create table JIReportUnitResource (
//        report_unit_id number(19,0) not null,
//        resource_id number(19,0) not null,
//        resource_index number(10,0) not null,
//        primary key (report_unit_id, resource_index)
//    );

//     create table JIReportUnitInputControl (
//        report_unit_id number(19,0) not null,
//        input_control_id number(19,0) not null,
//        control_index number(10,0) not null,
//        primary key (report_unit_id, control_index)
//    );
//


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


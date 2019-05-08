package com.thing2x.rptsvr.repo.db

import java.sql.{Blob, Clob, Date}

import com.thing2x.rptsvr.repo.db.DBRepository._
import slick.lifted.ProvenShape
import slick.jdbc.H2Profile.api._

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
                                   version: Int = -1,
                                   id: Long = 0L)

final class JIResourceFolderTable(tag: Tag) extends Table[JIResourceFolder](tag, "JIResourceFolder") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def version = column[Int]("version")
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
                             childrenFolder: Option[Long],
                             label: String,
                             descriptoin: Option[String],
                             resourceType: String,
                             creationDate: Date = new Date(System.currentTimeMillis),
                             updateDate: Date = new Date(System.currentTimeMillis),
                             version: Int = -1,
                             id: Long = 0L)

final class JIResourceTable(tag: Tag) extends Table[JIResource](tag, "JIResource") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def version = column[Int]("version")
  def name = column[String]("name")
  def parentFolder = column[Long]("parent_folder")
  def childrenFolder = column[Option[Long]]("childrenFolder")
  def label = column[String]("label")
  def description = column[Option[String]]("description")
  def resourceType = column[String]("resourceType")
  def creationDate = column[Date]("creation_date")
  def updateDate = column[Date]("update_date")

  def parentFolderFk = foreignKey("resource_parent_folder_fk", parentFolder, resourceFolders)(_.id)

  def * = (name, parentFolder, childrenFolder, label, description, resourceType, creationDate, updateDate, version, id).mapTo[JIResource]
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
                                 reference: Option[Long],
                                 id: Long = 0L)

final class JIFileResourceTable(tag: Tag) extends Table[JIFileResource](tag, "JIFileResource") {
  def fileType    = column[String]("file_type")
  def data   = column[Option[Blob]]("data")
  def reference    = column[Option[Long]]("reference")
  def id           = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("fileresource_id_fk", id, resources)(_.id)

  def * : ProvenShape[JIFileResource] = (fileType, data, reference, id).mapTo[JIFileResource]
}

// create table JIJdbcDatasource (
//        id number(19,0) not null,
//        driver nvarchar2(100) not null,
//        password nvarchar2(250),
//        connectionUrl nvarchar2(500),
//        username nvarchar2(100),
//        timezone nvarchar2(100),
//        primary key (id)
//    );
final case class JIJdbcDatasource( driver: String,
                                   connectionUrl: Option[String],
                                   username: Option[String],
                                   password: Option[String],
                                   timezone: Option[String],
                                   id: Long = 0L)

final class JIJdbcDatasourceTable(tag: Tag) extends Table[JIJdbcDatasource](tag, "JIJdbcDatasource") {
  def driver = column[String]("driver")
  def connectionUrl = column[Option[String]]("connectionUrl")
  def username = column[Option[String]]("username")
  def password = column[Option[String]]("password")
  def timezone = column[Option[String]]("timezone")
  def id = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("jdbcdtatsource_id_fk", id, resources)(_.id)

  def * : ProvenShape[JIJdbcDatasource] = (driver, connectionUrl, username, password, timezone, id).mapTo[JIJdbcDatasource]
}

// create table JIQuery (
//        id number(19,0) not null,
//        dataSource number(19,0),
//        query_language nvarchar2(40) not null,
//        sql_query nclob not null,
//        primary key (id)
//    );
final case class JIQuery( queryLanguage: String,
                          sqlQuery: Clob,
                          dataSource: Option[Long],
                          id: Long = 0L)
final class JIQueryTable(tag: Tag) extends Table[JIQuery](tag, "JIQuery") {
  def queryLanguage = column[String]("query_language")
  def sqlQuery = column[Clob]("sql_query")
  def dataSource = column[Option[Long]]("dataSource")
  def id = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("jiquery_id_fk", id, resources)(_.id)
  def dataSourceFk = foreignKey("jiquery_datasource_fk", dataSource, resources)(_.id.?)

  def * : ProvenShape[JIQuery] = (queryLanguage, sqlQuery, dataSource, id).mapTo[JIQuery]
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

// create table JIObjectPermission (
//        id number(19,0) not null,
//        uri nvarchar2(1000) not null,
//        recipientobjectclass nvarchar2(250),
//        recipientobjectid number(19,0),
//        permissionMask number(10,0) not null,
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


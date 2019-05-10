package com.thing2x.rptsvr.repo.db

import java.sql.Date

import com.thing2x.rptsvr.repo.db.DBSchema._
import com.thing2x.rptsvr.{FileResource, FolderResource, JdbcDataSourceResource, QueryResource, RepositoryContext, Resource}
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape

import scala.concurrent.duration._
import scala.language.implicitConversions

object DBSchema {
  val resources = TableQuery[JIResourceTable]
  val resourceInsert = resources returning resources.map(_.id)

  val resourceFolders = TableQuery[JIResourceFolderTable]
  val resourceFolderInsert = resourceFolders returning resourceFolders.map(_.id)

  val fileResources = TableQuery[JIFileResourceTable]
  val jdbcResources = TableQuery[JIJdbcDatasourceTable]
  val queryResources = TableQuery[JIQueryTable]

  val reportUnits = TableQuery[JIReportUnitTable]
  val reportUnitInsert = reportUnits returning reportUnits.map(_.id)

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
    implicit val ec = ctx.executionContext
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
                                   id: Long = 0L){
  def asApiModel(implicit context: RepositoryContext): FolderResource = {
    val fr = FolderResource(uri, label)
    fr.version = version
    fr.permissionMask = 1
    fr
  }
}

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

//////////////////////////////////////////
// ResouceKind
//////////////////////////////////////////
trait JIResourceKind[+T <: Resource] {
  def asApiModel(resource: JIResource, uri: String)(implicit context: RepositoryContext): T
}

object JIResourceObject {
  implicit def apply(p: (JIResourceFolder, JIResource, JIResourceKind[Resource]))(implicit context: RepositoryContext): JIResourceObject = {
    JIResourceObject(p._1, p._2, p._3)
  }
}

case class JIResourceObject(folder: JIResourceFolder, resource: JIResource, obj: JIResourceKind[Resource])(implicit context: RepositoryContext) {
  def asApiModel: Resource = {
    obj.asApiModel(resource, s"${folder.uri}/${resource.name}")
  }
}

//    create table JIFileResource (
//        id number(19,0) not null,
//        data blob,
//        file_type nvarchar2(20),
//        reference number(19,0),
//        primary key (id)
//    );
final case class JIFileResource( fileType: String,
                                 data: Option[Array[Byte]],
                                 reference: Option[Long],
                                 id: Long = 0L) extends JIResourceKind[FileResource] {
  override def asApiModel(resource: JIResource, uri: String)(implicit context: RepositoryContext): FileResource = {
    val fr = FileResource(uri, resource.label)
    fr.version = resource.version
    fr.permissionMask = 1
    fr.fileType = fileType
    fr
  }
}

final class JIFileResourceTable(tag: Tag) extends Table[JIFileResource](tag, "JIFileResource") {
  def fileType    = column[String]("file_type")
  def data   = column[Option[Array[Byte]]]("data", O.SqlType("BLOB"))
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
                                   id: Long = 0L) extends JIResourceKind[JdbcDataSourceResource] {
  override def asApiModel(resource: JIResource, uri: String)(implicit context: RepositoryContext): JdbcDataSourceResource = {
    val fr = JdbcDataSourceResource(uri, resource.label)
    fr.version = resource.version
    fr.permissionMask = 1
    fr.driverClass = Some(driver)
    fr.connectionUrl = connectionUrl
    fr.username = username
    fr.password = password
    fr.timezone = timezone
    fr
  }
}

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
                          sqlQuery: String,
                          dataSource: Option[Long],
                          id: Long = 0L) extends JIResourceKind[QueryResource] {
  override def asApiModel(resource: JIResource, uri: String)(implicit context: RepositoryContext): QueryResource = {
    val fr = QueryResource(uri, resource.label)
    fr.version = resource.version
    fr.permissionMask = 1
    fr.query = sqlQuery
    fr.language = queryLanguage
    fr.dataSource = None
    fr
  }
}

final class JIQueryTable(tag: Tag) extends Table[JIQuery](tag, "JIQuery") {
  def queryLanguage = column[String]("query_language")
  def sqlQuery = column[String]("sql_query", O.SqlType("CLOB"))
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
final case class JIDataType( dataType: Int,
                             maxLength: Option[Long],
                             decimals: Option[Long],
                             regularExpr: Option[String],
                             minValue: Option[Array[Byte]],
                             maxValue: Option[Array[Byte]],
                             strictMin: Boolean,
                             strictMax: Boolean,
                             id: Long = 0L)

final class JIDataTypeTable(tag: Tag) extends Table[JIDataType](tag, "JIDataType") {
  def dataType = column[Int]("type")
  def maxLength = column[Option[Long]]("maxLength")
  def decimals = column[Option[Long]]("decimals")
  def regularExpr = column[Option[String]]("regularExpr")
  def minValue = column[Option[Array[Byte]]]("minValue")
  def maxValue = column[Option[Array[Byte]]]("max_value")
  def strictMin = column[Boolean]("strictMin")
  def strictMax = column[Boolean]("strictMax")
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def * : ProvenShape[JIDataType] = (dataType, maxLength, decimals, regularExpr, minValue, maxValue, strictMin, strictMax, id).mapTo[JIDataType]
}

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
final case class JIInputControl( controlType: Int,
                                 dataType: Option[Long],
                                 listOfValues: Option[Long],
                                 listQuery: Option[Long],
                                 queryValueColumn: Option[String],
                                 defaultValue: Option[Array[Byte]],
                                 mandatory: Boolean,
                                 readOnly: Boolean,
                                 visible: Boolean,
                                 id: Long = 0L)

final class JIInputControlTable(tag: Tag) extends Table[JIInputControl](tag, "JIInputControl") {
  def controlType = column[Int]("type")
  def dataType = column[Option[Long]]("data_type")
  def listOfValues = column[Option[Long]]("list_of_values")
  def listQuery = column[Option[Long]]("list_query")
  def queryValueColumn = column[Option[String]]("query_value_column")
  def defaultValue = column[Option[Array[Byte]]]("defaultValue")
  def mandatory = column[Boolean]("mandatory")
  def readOnly = column[Boolean]("readOnly")
  def visible = column[Boolean]("visible")
  def id = column[Long]("id", O.PrimaryKey)

  def * : ProvenShape[JIInputControl] = (controlType, dataType, listOfValues, listQuery, queryValueColumn, defaultValue, mandatory, readOnly, visible, id).mapTo[JIInputControl]
}
// create table JIInputControlQueryColumn (
//        input_control_id number(19,0) not null,
//        query_column nvarchar2(200) not null,
//        column_index number(10,0) not null,
//        primary key (input_control_id, column_index)
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
final case class JIReportUnit( reportDataSource: Option[Long],
                               query: Option[Long],
                               mainReport: Option[Long],
                               controlRenderer: Option[String],
                               reportRenderer: Option[String],
                               promptControls: Boolean,
                               controlsLayout: Int,
                               dataSnapshotId: Option[Long],
                               id: Long = 0L)

final class JIReportUnitTable(tag: Tag) extends Table[JIReportUnit](tag, "JIReportUnit") {
  def reportDataSource = column[Option[Long]]("reportDataSource")
  def query = column[Option[Long]]("query")
  def mainReport = column[Option[Long]]("mainReport")
  def controlRenderer = column[Option[String]]("controlrenderer")
  def reportRenderer = column[Option[String]]("reportrenderer")
  def promptControls = column[Boolean]("promptcontrols")
  def controlsLayout = column[Int]("controlslayout")
  def dataSnapshotId = column[Option[Long]]("data_snapshot_id")
  def id = column[Long]("id", O.PrimaryKey)

  def * : ProvenShape[JIReportUnit] = (reportDataSource, query, mainReport, controlRenderer, reportRenderer, promptControls, controlsLayout, dataSnapshotId, id).mapTo[JIReportUnit]
}

//    create table JIReportUnitResource (
//        report_unit_id number(19,0) not null,
//        resource_id number(19,0) not null,
//        resource_index number(10,0) not null,
//        primary key (report_unit_id, resource_index)
//    );
final case class JIReportUnitResource ( reportUnitId: Long,
                                        resourceId: Long,
                                        resourceIndex: Int)

final class JIReportUnitResourceTable(tag: Tag) extends Table[JIReportUnitResource](tag, "JIReportUnitResource") {
  def reportUnitId = column[Long]("report_unit_id")
  def resourceId = column[Long]("resource_id")
  def resourceIndex = column[Int]("resource_index")

  def * : ProvenShape[JIReportUnitResource] = (reportUnitId, resourceId, resourceIndex).mapTo[JIReportUnitResource]
}

//     create table JIReportUnitInputControl (
//        report_unit_id number(19,0) not null,
//        input_control_id number(19,0) not null,
//        control_index number(10,0) not null,
//        primary key (report_unit_id, control_index)
//    );
final case class JIReportUnitInputControl ( reportUnitId: Long,
                                            inputControlId: Long,
                                            controlIndex: Int)

final class JIReportUnitInputControlTable(tag: Tag) extends Table[JIReportUnitInputControl](tag, "JIReportUnitInputControl") {
  def reportUnitId = column[Long]("report_unit_id")
  def inputControlId = column[Long]("input_control_id")
  def controlIndex = column[Int]("control_index")

  def * : ProvenShape[JIReportUnitInputControl] = (reportUnitId, inputControlId, controlIndex).mapTo[JIReportUnitInputControl]
}

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


package com.thing2x.rptsvr

import java.io.File

import akka.http.scaladsl.model.ContentType

sealed trait Resource {
  val uri: String
  val label: String
  val permissionMask: Int
  val description: Option[String]
  val version: Int
  val creationDate: String
  val updateDate: String
}

case class FolderResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String) extends Resource

case class FileResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String) extends Resource

case class FileContent(uri: String, file: File, contentType: ContentType)

case class JndiDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                                  jndiName: String, timezone: String) extends Resource

case class JdbcDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                                  driverClass: String, username: String, password: String, connectionUrl: String, timezone: String) extends Resource

case class AwsDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                                 driverClass: String, username: String, password: String, connectionUrl: String, timezone: String,
                                 accessKey: String, secretKey: String, roleArn: String, region: String, dbName: String, dbInstanceIdentifier: String, dbService: String) extends Resource

case class SubDataSource(id: String, uri: String)

case class VirtualDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                                     subDataSources: Seq[SubDataSource]) extends Resource

case class BeanDataSourceResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                                  beanName: String, beanMethod: String) extends Resource

case class DatatypesResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                             pattern: String, maxValue: String, strictMax: Boolean, minValue: String, strictMin: Boolean, maxLength: Int) extends Resource

case class ReferenceUri(uri: String)

case class DataSourceReference(dataSourceReference: ReferenceUri)

case class QueryResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                         value: String, language: String, dataSource: DataSourceReference) extends Resource

case class JrxmlFile(jrxmlFile: Resource)
case class JrxmlResources(resource: Seq[String])

case class ReportUnitResource(uri: String, label: String, permissionMask: Int, description: Option[String], version: Int, creationDate: String, updateDate: String, `type`: String,
                              inputControls: Seq[String], alwaysPromptControls: Boolean, controlLayout: String, jrxml: JrxmlFile, resources: JrxmlResources) extends Resource

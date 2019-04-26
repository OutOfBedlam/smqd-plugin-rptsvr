package com.thing2x.rptsvr

sealed trait Resource {
  val uri: String
  val label: String
  val permissionMask: Int
  val version: Int
}

case class CreateFolderRequest(uri: String, label: String, permissionMask: Int, version: Int) extends Resource

case class CreateFileRequest(uri: String, label: String, permissionMask: Int, version: Int, `type`: String, content: String) extends Resource

case class ResourceLookupResponse(resourceLookup: Seq[Resource])

case class FolderResource(uri: String,
                          label: String,
                          permissionMask: Int,
                          description: String,
                          creationDate: String,
                          updateDate: String,
                          version: Int,
                          resourceType: String = "folder"
                         ) extends Resource

case class ReportUnitResource(uri: String,
                              label: String,
                              permissionMask: Int,
                              creationDate: String,
                              updateDate: String,
                              version: Int,
                              resourceType: String = "reportUnit"
                             ) extends Resource

case class DSJdbcResource(uri: String,
                          label: String,
                          permissionMask: Int,
                          driverClass: String,
                          connectionUrl: String,
                          username: String,
                          password: String,
                          version: Int
                         ) extends Resource

case class FileResource(uri: String,
                        label: String,
                        permissionMask: Int,
                        creationDate: String,
                        updateDate: String,
                        version: Int,
                        `type`: String) extends Resource

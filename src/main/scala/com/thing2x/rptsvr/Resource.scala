package com.thing2x.rptsvr

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}

trait ResourceCodec {
  implicit val resourceEncoder: Encoder[Resource] = new Encoder[Resource] {
    override def apply(resource: Resource): Json = {
      resource match {
        case r: FolderResource => r.asJson
        case r: FileResource => r.asJson
        case r: DSJdbcResource => r.asJson
        case r: ReportUnitResource => r.asJson
      }
    }
  }
}

trait Resource {
  val uri: String
  val label: String
  val permissionMask: Int
  val version: Int
}

case class CreateFolderRequest(uri: String, label: String, permissionMask: Int, version: Int) extends Resource

case class CreateFileRequest(uri: String, label: String, permissionMask: Int, version: Int, `type`: String, content: String) extends Resource

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

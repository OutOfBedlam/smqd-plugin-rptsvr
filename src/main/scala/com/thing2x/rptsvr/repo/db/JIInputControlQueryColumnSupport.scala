package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape

import scala.concurrent.Future

// create table JIInputControlQueryColumn (
//        input_control_id number(19,0) not null,
//        query_column nvarchar2(200) not null,
//        column_index number(10,0) not null,
//        primary key (input_control_id, column_index)
//    );
final case class JIInputControlQueryColumn ( inputControlId: Long,
                                             queryColumn: String,
                                             columnIndex: Int)

final class JIInputControlQueryColumnTable(tag: Tag) extends Table[JIInputControlQueryColumn](tag, "JIInputControlQueryColumn") {
  def inputControlId = column[Long]("input_control_id")
  def queryColumn = column[String]("query_column")
  def columnIndex = column[Int]("column_index")

  def pk = primaryKey("jiinputcontrolquerycolumn_pk", (inputControlId, columnIndex))

  def * : ProvenShape[JIInputControlQueryColumn] = (inputControlId, queryColumn, columnIndex).mapTo[JIInputControlQueryColumn]
}

final case class JIInputControlQueryColumnModel(icqc: JIInputControlQueryColumn, resource: JIResource, uri: String)

trait JIInputControlQueryColumnSupport { mySelf: DBRepository =>

  def selectInputControlQueryColumn(path: String): Future[JIInputControlQueryColumnModel] = selectInputControlQueryColumn(Left(path))

  def selectInputControlQueryColumn(id: Long): Future[JIInputControlQueryColumnModel] = selectInputControlQueryColumn(Right(id))

  private def selectInputControlQueryColumn(pathOrId: Either[String, Long]): Future[JIInputControlQueryColumnModel] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          icqc     <- inputControlQueryColumns.filter(_.inputControlId === resource.id)
        } yield (icqc, resource, folder)
      case Right(id) =>
    }
    ???
  }

}

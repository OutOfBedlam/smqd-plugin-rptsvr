package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.InputControlResource
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
  def inputControlIdFk = foreignKey("jiinputcontrolquerycolumn_input_control_id_fk", inputControlId, inputControls)(_.id)

  def * : ProvenShape[JIInputControlQueryColumn] = (inputControlId, queryColumn, columnIndex).mapTo[JIInputControlQueryColumn]
}

trait JIInputControlQueryColumnSupport { mySelf: DBRepository =>

  def selectInputControlQueryColumnModel(path: String): Future[Seq[InputControlResource]] = selectInputControlQueryColumnModel(Left(path))

  def selectInputControlQueryColumnModel(id: Long): Future[Seq[InputControlResource]] = selectInputControlQueryColumnModel(Right(id))

  private def selectInputControlQueryColumnModel(pathOrId: Either[String, Long]): Future[Seq[InputControlResource]] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          ic       <- inputControls.filter(_.id === resource.id)
          icqc     <- inputControlQueryColumns.filter(_.inputControlId === ic.id).sortBy(_.columnIndex)
        } yield (icqc, ic, resource, folder)
      case Right(id) =>
        for {
          icqc     <- inputControlQueryColumns.filter(_.inputControlId === id).sortBy(_.columnIndex)
          ic       <- icqc.inputControlIdFk
          resource <- ic.idFk
          folder   <- resource.parentFolderFk
        } yield (icqc, ic, resource, folder)
    }
    dbContext.run(action.result).map{ //x => case (icqc, ic, resource, folder) =>
      ???
    }
  }

  def selectInputControlQueryColumn(path: String): Future[Seq[JIInputControlQueryColumn]] = selectInputControlQueryColumn(Left(path))

  def selectInputControlQueryColumn(id: Long): Future[Seq[JIInputControlQueryColumn]] = selectInputControlQueryColumn(Right(id))

  private def selectInputControlQueryColumn(pathOrId: Either[String, Long]): Future[Seq[JIInputControlQueryColumn]] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          ic       <- inputControls.filter(_.id === resource.id)
          icqc     <- inputControlQueryColumns.filter(_.inputControlId === ic.id).sortBy(_.columnIndex)
        } yield icqc
      case Right(id) =>
        for {
          icqc     <- inputControlQueryColumns.filter(_.inputControlId === id).sortBy(_.columnIndex)
        } yield icqc
    }
    dbContext.run(action.result)
  }

  def insertInputControlQueryColumn(icqcList: Seq[JIInputControlQueryColumn]): Future[Option[Int]] = {
    val action = inputControlQueryColumns ++= icqcList
    dbContext.run(action)
  }
}

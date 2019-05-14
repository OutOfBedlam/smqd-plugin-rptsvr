package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.InputControlResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.lifted.ProvenShape

import scala.concurrent.Future

import DBSchema.profile.api._

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

  def pk = primaryKey("JIReportUnitInputControl_pk", (reportUnitId, controlIndex))

  def inputControlIdFk = foreignKey("JIReportUnitInputControl_input_control_id_fk", inputControlId, inputControls)(_.id)
  def reportUnitIdFk = foreignKey("JIReportUnitInputControl_report_unit_id_fk", reportUnitId, reportUnits)(_.id)

  def * : ProvenShape[JIReportUnitInputControl] = (reportUnitId, inputControlId, controlIndex).mapTo[JIReportUnitInputControl]
}

trait JIReportUnitInputControlSupport { mySelf: DBRepository =>

  def selectReportUnitInputControlModel(reportUnitId: Long): Future[Seq[InputControlResource]] = {
    val action = reportUnitInputControls.filter(_.reportUnitId === reportUnitId).sortBy(_.controlIndex).map(_.inputControlId)
    dbContext.run(action.result).flatMap( icIdList => Future.sequence( icIdList.map( selectInputControlModel ) ))
  }

  def selectReportUnitInputControl(reportUnitId: Long): Future[Seq[JIReportUnitInputControl]] = {
    val action = reportUnitInputControls.filter(_.reportUnitId === reportUnitId).sortBy(_.controlIndex)
    dbContext.run(action.result)
  }

  def insertReportUnitInputControl(reportUnitId: Long, icrList: Seq[InputControlResource]): Future[Seq[Long]] = {
    val r = icrList.zipWithIndex
    Future.sequence( r.map { case (fr, index) =>
      for {
        ctlId  <- insertInputControl(fr)
        ruicId <- insertReportUnitInputControl( JIReportUnitInputControl(reportUnitId, ctlId, index) )
      } yield ruicId
    })
  }

  def insertReportUnitInputControl(ctl: JIReportUnitInputControl): Future[Long] = {
    val action = reportUnitInputControls += ctl
    dbContext.run(action).map( _ => ctl.inputControlId )
  }
}
// Copyright (C) 2019  UANGEL
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Lesser Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.InputControlResource
import slick.lifted.ProvenShape

import scala.concurrent.Future

//     create table JIReportUnitInputControl (
//        report_unit_id number(19,0) not null,
//        input_control_id number(19,0) not null,
//        control_index number(10,0) not null,
//        primary key (report_unit_id, control_index)
//    );
final case class JIReportUnitInputControl ( reportUnitId: Long,
                                            inputControlId: Long,
                                            controlIndex: Int)

trait JIReportUnitInputControlSupport { mySelf: DBRepository =>
  import dbContext.profile.api._

  final class JIReportUnitInputControlTable(tag: Tag) extends Table[JIReportUnitInputControl](tag, "JIREPORTUNITINPUTCONTROL") {
    def reportUnitId = column[Long]("REPORT_UNIT_ID")
    def inputControlId = column[Long]("INPUT_CONTROL_ID")
    def controlIndex = column[Int]("CONTROL_INDEX")

    def pk = primaryKey("JIREPORTUNITINPUTCONTROL_PK", (reportUnitId, controlIndex))

    def inputControlIdFk = foreignKey("JIREPORTUNITINPUTCONTROL_INPUT_CONTROL_ID_FK", inputControlId, inputControls)(_.id)
    def reportUnitIdFk = foreignKey("JIREPORTUNITINPUTCONTROL_REPORT_UNIT_ID_FK", reportUnitId, reportUnits)(_.id)

    def * : ProvenShape[JIReportUnitInputControl] = (reportUnitId, inputControlId, controlIndex).mapTo[JIReportUnitInputControl]
  }

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
package com.thing2x.rptsvr.repo.db

import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape


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


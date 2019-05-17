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

import com.thing2x.rptsvr.FileResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.lifted.ProvenShape

import scala.concurrent.Future

import DBSchema.profile.api._

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

  def pk = primaryKey("JIReportUnitResource_pk", (reportUnitId, resourceIndex))

  def reportUnitIdFk = foreignKey("JIReportUnitResource_reportUnitId_fk", reportUnitId, reportUnits)(_.id)
  def resourceIdFk = foreignKey("JIReportUnitResource_resourceId_fk", resourceId, fileResources)(_.id)

  def * : ProvenShape[JIReportUnitResource] = (reportUnitId, resourceId, resourceIndex).mapTo[JIReportUnitResource]
}

trait JIReportUnitResourceSupport { mySelf: DBRepository =>

  def selectReportUnitResourceModel(reportUnitId: Long): Future[Seq[FileResource]] = {
    for {
      resourceIdList <- selectReportUnitResource(reportUnitId).map(_.map(_.resourceId))
      resourceList   <- selectFileResourceModelList(resourceIdList)
    } yield resourceList
  }

  def selectReportUnitResource(reportUnitId: Long): Future[Seq[JIReportUnitResource]] = {
    val action = reportUnitResources.filter(_.reportUnitId === reportUnitId).sortBy(_.resourceIndex)
    dbContext.run(action.result)
  }

  def insertReportUnitResource(reportUnitId: Long, rurMap: Map[String, FileResource]): Future[Seq[Long]] = {
    val r = rurMap.values.toSeq.zipWithIndex
    Future.sequence( r.map { case (fr, index) =>
      for {
        fileId <- insertFileResource(fr)
        rur    <- insertReportUnitResource( JIReportUnitResource(reportUnitId, fileId, index) )
      } yield rur
    })
  }

  def insertReportUnitResource(rur: JIReportUnitResource): Future[Long] = {
    val action = reportUnitResources += rur
    dbContext.run(action).map( _ => rur.resourceId )
  }
}
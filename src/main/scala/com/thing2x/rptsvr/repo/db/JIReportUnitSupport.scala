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
import java.util.Base64

import com.thing2x.rptsvr.ReportUnitResource
import slick.lifted.ProvenShape

import scala.concurrent.Future


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

trait JIReportUnitSupport { mySelf: DBRepository =>
  import dbContext.profile.api._


  final class JIReportUnitTable(tag: Tag) extends Table[JIReportUnit](tag, dbContext.table("JIReportUnit")) {
    def reportDataSource = column[Option[Long]]("REPORTDATASOURCE")
    def query = column[Option[Long]]("QUERY")
    def mainReport = column[Option[Long]]("MAINREPORT")
    def controlRenderer = column[Option[String]]("CONTROLRENDERER")
    def reportRenderer = column[Option[String]]("REPORTRENDERER")
    def promptControls = column[Boolean]("PROMPTCONTROLS")
    def controlsLayout = column[Int]("CONTROLSLAYOUT")
    def dataSnapshotId = column[Option[Long]]("DATA_SNAPSHOT_ID")
    def id = column[Long]("ID", O.PrimaryKey)

    def idFk = foreignKey("JIREPORTUNIT_ID_FK", id, resources)(_.id)
    def queryFk = foreignKey("JIREPORTUNIT_QUERY_FK", query, queryResources)(_.id.?)
    def reportDataSourceFk = foreignKey("JIREPORTUNIT_DATASOURCE_FK", reportDataSource, resources)(_.id.?)
    def mainReportFk = foreignKey("JIREPORTUNIT_MAINREPORT_FK", mainReport, fileResources)(_.id.?)

    def * : ProvenShape[JIReportUnit] = (reportDataSource, query, mainReport, controlRenderer, reportRenderer, promptControls, controlsLayout, dataSnapshotId, id).mapTo[JIReportUnit]
  }

  def selectReportUnitModel(path: String): Future[ReportUnitResource] = selectReportUnitModel(Left(path))

  def selectReportUnitModel(id: Long): Future[ReportUnitResource] = selectReportUnitModel(Right(id))

  private def selectReportUnitModel(pathOrId: Either[String, Long]): Future[ReportUnitResource] = {
    val futureReportUnit = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder <- selectResourceFolder(folderPath)
          resource <- selectResource(path)
          reportUnit <- selectReportUnit(resource.id)
        } yield(reportUnit, resource, folder)
      case Right(id) =>
        for {
          reportUnit              <- selectReportUnit(id)
          resource                <- selectResource(id)
          folder                  <- selectResourceFolder(resource.parentFolder)
        } yield(reportUnit, resource, folder)
    }

    futureReportUnit.flatMap { case (reportUnit, resource, folder) =>
      val x= for {
        reportUnitInputControls <- selectReportUnitInputControlModel(resource.id)
        reportUnitResources     <- selectReportUnitResourceModel(resource.id)
        jrxml                   <- reportUnit.mainReport match {
          case Some(mainReportId) => selectFileResourceModel(mainReportId).map( Some(_) )
          case None => Future( None )
        }
        ds                      <- reportUnit.reportDataSource match {
          case Some(dsId) => selectDataSourceModel(dsId).map( Some(_) )
          case None => Future( None )
        }
        query                   <- reportUnit.query match {
          case Some(queryId) => selectQueryResourceModel(queryId).map( Some(_) )
          case None => Future( None )
        }
      } yield (jrxml, ds, reportUnitResources, reportUnitInputControls, query)
      x.map{ case (jrxml, ds, rurs, ruic, query) =>
        val fr = ReportUnitResource(s"${folder.uri}/${resource.name}", resource.label)
        fr.resources = rurs.map(r => (r.label, r)).toMap
        fr.inputControls = ruic
        fr.jrxml = jrxml
        fr.dataSource = ds
        fr.query = query
        fr.creationDate = resource.creationDate
        fr.updateDate = resource.updateDate
        fr.description = resource.description
        fr
      }
    }
  }

  def selectReportUnit(path: String): Future[JIReportUnit] = selectReportUnit(Left(path))

  def selectReportUnit(id: Long): Future[JIReportUnit] = selectReportUnit(Right(id))

  private def selectReportUnit(pathOrId: Either[String, Long]): Future[JIReportUnit] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          reportUnit <- reportUnits.filter(_.id === resource.id)
        } yield reportUnit
      case Right(id) =>
        for {
          reportUnit <- reportUnits.filter(_.id === id)
        } yield reportUnit
    }
    dbContext.run(action.result.head)
  }

  def insertReportUnit(request: ReportUnitResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val filesFolderName = s"${name}_files"
    val filesFolderPath = s"$parentFolderPath/${name}_files"

    val mainDsId = request.dataSource match {
      case Some(ds) => selectJdbcDataSource(ds.uri).map( x => Some(x.id) )
      case None     => Future( None )
    }
    val queryId = request.query match {
      case Some(q)  => selectQueryResource(q.uri).map( x => Some(x.id) )
      case None     => Future( None)
    }
    val jrxmlContent = request.jrxml match {
      case Some(jrxml) if jrxml.content.isDefined => Some(Base64.getDecoder.decode(jrxml.content.get))
      case _ => None
    }

    val reportUnitId = for {
      parentFolderId  <- selectResourceFolder(parentFolderPath).map( _.id )
      dsId            <- mainDsId
      queryId         <- queryId
      // create _files folder
      filesFolderId   <- insertResourceFolder( JIResourceFolder(filesFolderPath, filesFolderName, filesFolderName, None, parentFolderId, hidden = true) )
      // save jrxml
      jrxmlResourceId <- insertResource( JIResource(request.jrxml.get.label, filesFolderId, None, request.jrxml.get.label, request.jrxml.get.description, DBResourceTypes.file, version = request.jrxml.get.version + 1))
      _               <- insertFileResource( JIFileResource("jrxml", jrxmlContent, None, jrxmlResourceId) )
      // save report unit resource
      reportUnitId    <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.reportUnit, version = request.version + 1) )
      _               <- insertReportUnit( JIReportUnit(dsId, queryId, Some(jrxmlResourceId), None, None, request.alwaysPromptControls, request.conrolsLayoutId, None, reportUnitId))
      // save resource files
      _               <- insertReportUnitResource( reportUnitId, request.resources )
      // save inputControls
      _               <- insertReportUnitInputControl( reportUnitId, request.inputControls )

    } yield reportUnitId

    reportUnitId
  }

  def insertReportUnit(ru: JIReportUnit): Future[Long] = {
    val action = reportUnits += ru
    dbContext.run(action).map( _ => ru.id )
  }

}

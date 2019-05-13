package com.thing2x.rptsvr.repo.db
import java.util.Base64

import com.thing2x.rptsvr.ReportUnitResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._
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

final class JIReportUnitTable(tag: Tag) extends Table[JIReportUnit](tag, "JIReportUnit") {
  def reportDataSource = column[Option[Long]]("reportDataSource")
  def query = column[Option[Long]]("query")
  def mainReport = column[Option[Long]]("mainReport")
  def controlRenderer = column[Option[String]]("controlrenderer")
  def reportRenderer = column[Option[String]]("reportrenderer")
  def promptControls = column[Boolean]("promptcontrols")
  def controlsLayout = column[Int]("controlslayout")
  def dataSnapshotId = column[Option[Long]]("data_snapshot_id")
  def id = column[Long]("id", O.PrimaryKey)

  def * : ProvenShape[JIReportUnit] = (reportDataSource, query, mainReport, controlRenderer, reportRenderer, promptControls, controlsLayout, dataSnapshotId, id).mapTo[JIReportUnit]
}



trait ReportUnitTableSupport { mySelf: DBRepository =>

  def insertReportUnit(request: ReportUnitResource): Future[Option[ReportUnitResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val filesFolderName = s"${name}_files"
    val filesFolderPath = s"$parentFolderPath/${name}_files"

    val mainDsId = request.dataSource match {
      case Some(ds) => selectDataSourceResource(ds.uri).map(x => Some(x.resource.id) )
      case None     => Future( None )
    }
    val queryId = request.query match {
      case Some(q)  => selectQueryResource(q.uri).map( x => Some(x.resource.id))
      case None     => Future( None)
    }
    val jrxmlContent = request.jrxml match {
      case Some(jrxml) if jrxml.content.isDefined => Some(Base64.getDecoder.decode(jrxml.content.get))
      case _ => None
    }

    val reportUnitId = for {
      parentFolderId <- selectResourceFolder(parentFolderPath).map( _.id )
      dsId           <- mainDsId
      queryId        <- queryId
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, JIResourceTypes.reportUnit, version = request.version + 1) )
      _              <- insertReportUnit( JIReportUnit(dsId, queryId, None, None, None, request.alwaysPromptControls, request.conrolsLayoutId, None, resourceId))
      filesFolderId  <- insertResourceFolder( JIResourceFolder(filesFolderPath, filesFolderName, filesFolderName, None, parentFolderId, hidden = true) )
      _              <- insertFileResource( JIFileResource(JIResourceTypes.reportUnit, jrxmlContent, None) )
    } yield resourceId

    //    request.dataSource
    //    request.inputControls
    //    request.resources
    //    request.jrxml
    ???
  }

  def insertReportUnit(ru: JIReportUnit): Future[Long] = {
    val action = reportUnits += ru
    dbContext.run(action).map( _ => ru.id )
  }

  /////////////////////////////////////////////////////////////////////////////
  // ReportUnitInputControl
  /////////////////////////////////////////////////////////////////////////////

  /*
  1 Boolean None
   */
}

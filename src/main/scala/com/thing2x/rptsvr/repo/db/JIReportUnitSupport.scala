package com.thing2x.rptsvr.repo.db
import java.util.Base64

import com.thing2x.rptsvr.ReportUnitResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.lifted.ProvenShape

import scala.concurrent.Future

import DBSchema.profile.api._

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

  def idFk = foreignKey("jireportunit_id_fk", id, resources)(_.id)
  def queryFk = foreignKey("jireportunit_query_fk", query, queryResources)(_.id.?)
  def reportDataSourceFk = foreignKey("jireportunit_datasource_fk", reportDataSource, resources)(_.id.?)
  def mainReportFk = foreignKey("jireportunit_mainreport_fk", mainReport, fileResources)(_.id.?)

  def * : ProvenShape[JIReportUnit] = (reportDataSource, query, mainReport, controlRenderer, reportRenderer, promptControls, controlsLayout, dataSnapshotId, id).mapTo[JIReportUnit]
}


trait JIReportUnitSupport { mySelf: DBRepository =>

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
      } yield (jrxml, ds, reportUnitResources, reportUnitInputControls)
      x.map{ case (jrxml, ds, rurs, ruic) =>
        val fr = ReportUnitResource(s"${folder.uri}/${resource.name}", resource.label)
        fr.resources = rurs.map(r => (r.label, r)).toMap
        fr.inputControls = ruic
        fr.jrxml = jrxml
        fr.dataSource = ds
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

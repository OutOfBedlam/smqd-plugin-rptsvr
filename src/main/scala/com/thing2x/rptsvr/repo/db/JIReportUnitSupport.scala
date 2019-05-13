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

  def idFk = foreignKey("jireportunit_id_fk", id, resources)(_.id)
  def queryFk = foreignKey("jireportunit_query_fk", query, queryResources)(_.id.?)
  def reportDataSourceFk = foreignKey("jireportunit_datasource_fk", reportDataSource, resources)(_.id.?)
  def mainReportFk = foreignKey("jireportunit_mainreport_fk", mainReport, fileResources)(_.id.?)

  def * : ProvenShape[JIReportUnit] = (reportDataSource, query, mainReport, controlRenderer, reportRenderer, promptControls, controlsLayout, dataSnapshotId, id).mapTo[JIReportUnit]
}


final case class JIReportUnitModel(reportUnit: JIReportUnit, resource: JIResource, uri: String) extends DBModelKind

trait JIReportUnitSupport { mySelf: DBRepository =>

  def asApiModel(model: JIReportUnitModel): ReportUnitResource = {
    val fr = ReportUnitResource(model.uri, model.resource.label)

    fr
  }

  def selectReportUnit(path: String): Future[JIReportUnitModel] = selectReportUnit(Left(path))

  def selectReportUnit(id: Long): Future[JIReportUnitModel] = selectReportUnit(Right(id))

  private def selectReportUnit(pathOrId: Either[String, Long]): Future[JIReportUnitModel] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder     <- resourceFolders.filter(_.uri === folderPath)
          resource   <- resources.filter(_.name === name)
          reportUnit <- reportUnits.filter(_.id === resource.id)
        } yield (reportUnit, resource, folder)
      case Right(id) =>
        for {
          reportUnit    <- reportUnits.filter(_.id === id)
          resource      <- reportUnit.idFk
          folder        <- resource.parentFolderFk
        } yield (reportUnit, resource, folder)
    }

    dbContext.run(action.result.head).map{ case (ru, resource, folder) =>
      JIReportUnitModel(ru, resource, s"${folder.uri}/${resource.name}")
    }
  }

  def insertReportUnit(request: ReportUnitResource): Future[Long] = {
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
      // save report unit resource
      resourceId     <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.reportUnit, version = request.version + 1) )
      _              <- insertReportUnit( JIReportUnit(dsId, queryId, None, None, None, request.alwaysPromptControls, request.conrolsLayoutId, None, resourceId))
      // create _files folder
      filesFolderId  <- insertResourceFolder( JIResourceFolder(filesFolderPath, filesFolderName, filesFolderName, None, parentFolderId, hidden = true) )
      // save jrxml resource
      jrxmlResourceId <- insertResource( JIResource(request.jrxml.get.label, filesFolderId, None, request.jrxml.get.label, request.jrxml.get.description, DBResourceTypes.file, version = request.jrxml.get.version + 1))
      _               <- insertFileResource( JIFileResource(DBResourceTypes.reportUnit, jrxmlContent, None, jrxmlResourceId) )
      // save resource files
//      _              <- request.resources.foreach { case (name, rsc) =>
//          insertFileResource( JIFileResource())
//      }
      // save inputControls
      //_ <- reportUnitInputControls
    } yield resourceId

    //    request.dataSource
    //    request.inputControls
    //    request.resources
    //    request.jrxml
    reportUnitId
  }

  def insertReportUnit(ru: JIReportUnit): Future[Long] = {
    val action = reportUnits += ru
    dbContext.run(action).map( _ => ru.id )
  }

}

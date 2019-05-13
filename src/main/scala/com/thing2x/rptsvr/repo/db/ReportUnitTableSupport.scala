package com.thing2x.rptsvr.repo.db
import java.util.Base64

import com.thing2x.rptsvr.ReportUnitResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait ReportUnitTableSupport { mySelf: DBRepository =>

  def insertReportUnit(request: ReportUnitResource): Future[Option[ReportUnitResource]] = {
    val (parentFolderPath, name) = splitPath(request.uri)
    val filesFolderName = s"${name}_files"
    val filesFolderPath = s"$parentFolderPath/${name}_files"

    val mainDsId = request.dataSource match {
      case Some(ds) => selectDataSource(ds.uri).map(x => Some(x.resource.id) )
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

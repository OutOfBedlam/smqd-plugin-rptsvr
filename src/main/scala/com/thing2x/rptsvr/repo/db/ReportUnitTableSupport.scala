package com.thing2x.rptsvr.repo.db
import com.thing2x.rptsvr.ReportUnitResource
import com.thing2x.rptsvr.repo.db.DBRepository.{reportUnitInsert, resourceFolderInsert}
import slick.dbio.DBIOAction
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

trait ReportUnitTableSupport { mySelf: DBRepository =>
  /////////////////////////////////////////////////////////////////////////////
  // ReportUnit
  /////////////////////////////////////////////////////////////////////////////

  def insertReportUnit(request: ReportUnitResource): Future[Option[ReportUnitResource]] = {
//    val dataSourceIdFuture = request.dataSource match {
//      case Some(ds) =>
//        selectResourceMeta(ds.uri).map {
//          case Some(m) => Some(m.id)
//          case _ => None
//        }
//      case _ => Future( None )
//    }
//
//    val (parentFolderPath, name) = splitPath(request.uri)
//    val filesFolderName = s"${name}_files"
//    val filesFolderPath = s"$parentFolderPath/${name}_files"
//
//    val filesFolderIdQuery = for {
//      parentFolderId <- DBIOAction.from( selectResourceFolder(parentFolderPath).map(_.id) )
//      filesFolderId <- resourceFolderInsert += JIResourceFolder(filesFolderPath, filesFolderName, filesFolderName, None, parentFolderId, true)
//    } yield filesFolderId
//
//    val ruQuery = for {
//      dataSourceId    <- DBIOAction.from( dataSourceIdFuture )
//      _               <- filesFolderIdQuery
//      jrxmlFileId     <- DBIOAction.from( insertFileResource( request.jrxml.get ))
//      jrxmlResourceId <- reportUnitInsert += JIReportUnit(dataSourceId, None, None, None, None, request.alwaysPromptControls, request.conrolsLayoutId, None)
//    } yield jrxmlResourceId
//
//    dbContext.run(ruQuery).map { ruId =>
//
//      logger.info(s"=============> $ruId")
//      ???
//    }
    //    request.dataSource
    //    request.inputControls
    //    request.resources
    //    request.jrxml
    ???
  }

  /////////////////////////////////////////////////////////////////////////////
  // ReportUnitInputControl
  /////////////////////////////////////////////////////////////////////////////

  /*
  1 Boolean None
   */
}

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

import com.thing2x.rptsvr.Repository.ResourceNotFoundException
import com.thing2x.rptsvr.{DataSourceResource, FileContent, FileResource, FolderResource, ListResult, QueryResource, ReportUnitResource, Repository, RepositoryContext, Resource, Result}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class DBRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging
  with JIResourceFolderSupport
  with JIResourceSupport
  with JIFileResourceSupport
  with JIDataSourceSupport
  with JIInputControlSupport
  with JIQuerySupport
  with JIDataTypeSupport
  with JIListOfValuesSupport
  with JIReportUnitSupport
  with JIReportUnitInputControlSupport
  with JIReportUnitResourceSupport {

  protected implicit val dbContext: DBRepositoryContext =
    new DBRepositoryContext(this, smqd, config)

  override val context: RepositoryContext = dbContext

  protected implicit val ec: ExecutionContext = context.executionContext

  override def start(): Unit = {
    dbContext.open() {
      logger.info("Deferred actions")
    }

    if (!dbContext.readOnly)
      DBSchema.createSchema(dbContext)
  }

  override def stop(): Unit = {
    dbContext.close()
  }

  private[db] def splitPath(path: String): (String, String) = {
    val paths = path.split('/')
    val parentFolderPath = paths.dropRight(1).mkString("/", "/", "").replaceAllLiterally("//", "/")
    val name = paths.last
    (parentFolderPath, name)
  }

  override def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]] = {
    for {
      fl <- selectSubFolderModelFromResourceFolder(path)
      rl <- selectResourcesFromResourceFolder(path).map( _.map { r => r.resourceType match {
        case DBResourceTypes.reportUnit =>
          ReportUnitResource(path + "/" + r.name, r.label)
        case DBResourceTypes.jdbcDataSource =>
          FileResource(path + "/" + r.name, r.label)
        case DBResourceTypes.file =>
          FileResource(path + "/" + r.name, r.label)
        case _ =>
          FileResource(path + "/" + r.name, r.label)
      }})
    } yield fl ++ rl
  }

  override def setResource(path: String, request: Resource, createFolders: Boolean, overwrite: Boolean): Future[Result[Resource]] = {
    logger.debug(s"setResource uri=$path, $request, resourceType=${request.resourceType}")
    request match {
      case req: FolderResource =>          insertResourceFolder(req).flatMap( selectResourceFolderModel ).map(Right(_))
      case req: FileResource =>            insertFileResource(req).flatMap( selectFileResourceModel ).map(Right(_))
      case req: DataSourceResource =>      insertDataSource(req).flatMap( selectDataSourceModel ).map(Right(_))
      case req: QueryResource =>           insertQueryResource(req).flatMap( selectQueryResourceModel ).map(Right(_))
      case req: ReportUnitResource =>      insertReportUnit(req).flatMap( selectReportUnitModel ).map(Right(_))
      case _ => Future( Left(new ResourceNotFoundException(path)) )
    }
  }

  override def getResource(path: String, isReferenced: Boolean): Future[Result[Resource]] = {
    logger.debug(s"getResource uri=$path, isReferenced=$isReferenced")
    // find folder with path, if it is not a folder use path as resource
    selectResourceFolderModelIfExists(path).flatMap {
      case Some(folder) =>
        logger.trace(s"getResource uri=$path, isReferenced=$isReferenced, isFolder=true")
        Future( Right(folder) )
      case None =>
        logger.trace(s"getResource uri=$path, isReferenced=$isReferenced, isFolder=false")
        selectResourceModel(path).map( Right(_) ).recover{
          case _: NoSuchElementException =>
            Left( new ResourceNotFoundException(path) )
          case ex =>
            logger.debug(s"resource not found: $path", ex)
            Left( ex )
        }
    }
  }

  override def getContent(path: String): Future[Either[Throwable, FileContent]] =
    selectFileContentModel(path).map( Right(_) )

  override def deleteResource(path: String): Future[Boolean] = ???
}

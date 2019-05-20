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
import com.thing2x.rptsvr._
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

class DBRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config)
  with Repository with DBSchema with StrictLogging {

  protected implicit val dbContext: DBRepositoryContext =
    new DBRepositoryContext(this, smqd, config)

  override val context: RepositoryContext = dbContext

  protected implicit val ec: ExecutionContext = context.executionContext

  val resources = TableQuery[JIResourceTable]

  val resourceFolders = TableQuery[JIResourceFolderTable]

  val fileResources = TableQuery[JIFileResourceTable]
  val jdbcResources = TableQuery[JIJdbcDatasourceTable]
  val queryResources = TableQuery[JIQueryTable]

  val reportUnits = TableQuery[JIReportUnitTable]

  val reportUnitResources = TableQuery[JIReportUnitResourceTable]
  val reportUnitInputControls = TableQuery[JIReportUnitInputControlTable]

  val dataTypes = TableQuery[JIDataTypeTable]
  val inputControls = TableQuery[JIInputControlTable]
  val inputControlQueryColumns = TableQuery[JIInputControlQueryColumnTable]
  val listOfValues = TableQuery[JIListOfValuesTable]

  private[db] val (e_cipher, d_cipher) = {
    val keyBytesHex = config.getString("cipher.secret_key")
    val ivBytesHex = config.getString("cipher.initvector")
    val algorithm = config.getString("cipher.algorithm")
    val transformation = config.getString("cipher.transformation")
    val keyBytes = keyBytesHex.split(" ").toSeq.map { tok =>
      val hex = tok.substring(2)
      Integer.parseInt(hex, 16).toByte
    }.toArray
    val ivBytes = ivBytesHex.split(" ").toSeq.map { tok =>
      val hex = tok.substring(2)
      Integer.parseInt(hex, 16).toByte
    }.toArray
    val key = new SecretKeySpec(keyBytes, algorithm)
    val iv  = new IvParameterSpec(ivBytes)
    val d = Cipher.getInstance(transformation)
    val e = Cipher.getInstance(transformation)
    d.init(Cipher.DECRYPT_MODE, key, iv)
    e.init(Cipher.ENCRYPT_MODE, key, iv)
    (e, d)
  }

  override def start(): Unit = {
    dbContext.open() {
      logger.info("Deferred actions")
    }

    if (!dbContext.readOnly)
      createSchema(dbContext)
  }

  override def stop(): Unit = {
    dbContext.close()
  }

  private[db] def decode(content: String): String = {
    val enc = dehexify(content)
    val passPlainText = new String(d_cipher.doFinal(enc), "UTF-8")
    passPlainText
  }

  private[db] def encode(content: String): String = {
    val enc = e_cipher.doFinal(content.getBytes("UTF-8"))
    hexify(enc)
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

package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.repo.db.DBRepository._
import com.thing2x.rptsvr.{FileContent, FileResource, FolderResource, ListResult, Repository, RepositoryContext, Resource, Result}
import com.thing2x.smqd.Smqd
import com.thing2x.smqd.plugin.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.LoggerFactory
import slick.jdbc.H2Profile.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object DBRepository {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val resources = TableQuery[JIResourceTable]
  val resourceInsert = resources returning resources.map(_.id)

  val resourceFolders = TableQuery[JIResourceFolderTable]
  val resourceFolderInsert = resourceFolders returning resourceFolders.map(_.id)

  val fileResources = TableQuery[JIFileResourceTable]
  val fileResourceInsert = fileResources returning fileResources.map(_.id)

  val contentResources = TableQuery[JIContentResourceTable]

  private val schema = Seq(
    resourceFolders.schema,
    resources.schema,
    fileResources.schema,
    contentResources.schema,
  )
}

class DBRepository(name: String, smqd: Smqd, config: Config) extends Service(name, smqd, config) with Repository with StrictLogging {

  private implicit val dbContext: DBRepositoryContext =
    new DBRepositoryContext(this, smqd, config)

  override val context: RepositoryContext = dbContext

  private implicit val ec: ExecutionContext = context.executionContext

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

  private def createSchema(ctx: DBRepositoryContext): Unit = {
//    schema.foreach { tbl =>
//      logger.info(tbl.createStatements.mkString)
//      Await.result(ctx.database.run(tbl.schema.create), 2.second)
//    }

    val setup = for {
      // create tables
      _                <- schema.reduce( _ ++ _).create
      // insert default tables
      rootFolderId     <- resourceFolderInsert += JIResourceFolder("/", "/", "/", Some("Root"), -1)
      _                <- resourceFolderInsert += JIResourceFolder("/public", "Public", "Public", Some("Public"), rootFolderId)
    } yield rootFolderId

    Await.result(ctx.database.run(setup), 8.second)
  }

  override def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]] = {
    val query = for {
      rsc <- resources
      folder <- rsc.folder if folder.uri === path
    } yield (folder, rsc)

    dbContext.database.run(query.result).map{ rset =>
      rset.map{ case (folder, rsc) =>
        rsc.resourceType match {
          case "folder" =>
            new FolderResource(folder.uri+"/"+rsc.id, rsc.label)
          case "file" | _ =>
            new FileResource(folder.uri+"/"+rsc.id, rsc.label)
        }
      }
    }
  }

  override def setResource(path: String, request: Resource, createFolders: Boolean, overwrite: Boolean): Future[Result[Resource]] = {
    logger.debug(s"setResource uri=$path, $request")

    val paths = request.uri.split('/')
    val folderPath = paths.dropRight(1).mkString("/", "/", "")
    val name = paths.last

    logger.debug(s"setResource folderPath=$folderPath, $name")

    request.resourceType match {
      case "folder" =>
        logger.debug(s"setResource folder=$folderPath")
        val action = for {
          folder <- resourceFolders if folder.uri === folderPath
//          rsc    <- resourcesInsert += JIResource(name, folder.id, -1, request.label, request.description, request.resourceType)
        } yield folder

        val result = dbContext.database.run(action.result)
        result.map(folder =>
          logger.info(s"======+> result: $folder")
        )
        //        dbContext.database.run(resourceFolders.filter(_.uri === request.uri).result).flatMap{ rset =>
        //          logger.debug(s"setResource uri=$path, $rset")
        //          if (rset.isEmpty) {
        //            resourceFolders += JIResourceFolder(request.version, request.uri, request.label, request.label, request.description, 1, false)
        //            val rsc = getResource(path, true)
        //            Right(rsc)
        //          }
        //          else {
        //            rset.head
        //            val rsc = getResource(path, true)
        //            Right(rsc)
        //          }
        //        }
        ???
      case "file" =>
        ???
    }
  }

  override def getResource(path: String, isReferenced: Boolean): Future[Result[Resource]] = ???

  override def getContent(path: String): Future[Either[Throwable, FileContent]] = ???

  override def deleteResource(path: String): Future[Boolean] = ???

}

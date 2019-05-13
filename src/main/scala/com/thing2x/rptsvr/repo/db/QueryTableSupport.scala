package com.thing2x.rptsvr.repo.db
import com.thing2x.rptsvr.QueryResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape

import scala.concurrent.Future

// create table JIQuery (
//        id number(19,0) not null,
//        dataSource number(19,0),
//        query_language nvarchar2(40) not null,
//        sql_query nclob not null,
//        primary key (id)
//    );
final case class JIQuery( queryLanguage: String,
                          sqlQuery: String,
                          dataSource: Option[Long],
                          id: Long = 0L)

final class JIQueryTable(tag: Tag) extends Table[JIQuery](tag, "JIQuery") {
  def queryLanguage = column[String]("query_language")
  def sqlQuery = column[String]("sql_query", O.SqlType("CLOB"))
  def dataSource = column[Option[Long]]("dataSource")
  def id = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("jiquery_id_fk", id, resources)(_.id)
  def dataSourceFk = foreignKey("jiquery_datasource_fk", dataSource, resources)(_.id.?)

  def * : ProvenShape[JIQuery] = (queryLanguage, sqlQuery, dataSource, id).mapTo[JIQuery]
}


final case class JIQueryModel(query: JIQuery, resource: JIResource, uri: String, ds: Option[JIDataSourceModel]) extends JIDataModelKind

trait QueryTableSupport { mySelf: DBRepository =>

  def asApiModel(model: JIQueryModel): QueryResource = {
    val fr = QueryResource(model.uri, model.resource.label)
    fr.version = model.resource.version
    fr.permissionMask = 1
    fr.query = model.query.sqlQuery
    fr.language = model.query.queryLanguage
    fr.dataSource = model.ds.map( asApiModel )
    fr
  }

  def selectQueryResource(path: String): Future[JIQueryModel] = selectQueryResource(Left(path))

  def selectQueryResource(id: Long): Future[JIQueryModel] = selectQueryResource(Right(id))

  private def selectQueryResource(pathOrId: Either[String, Long]): Future[JIQueryModel] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          query    <- queryResources.filter(_.id === resource.id)
        } yield (folder, resource, query)
      case Right(id) =>
        for {
          query    <- queryResources.filter(_.id === id)
          resource <- query.idFk
          folder   <- resource.parentFolderFk
        } yield (folder, resource, query)
    }

    dbContext.run(action.result.head).flatMap{ case(f, r, q) =>
      val dsFuture = q.dataSource match {
        case Some(dsId) => selectDataSourceResource(dsId).map( Some( _ ) )
        case _ => Future( None )
      }

      dsFuture.map( ds => JIQueryModel(q, r, s"${f.uri}/${r.name}", ds) )
    }
  }

  def insertQueryResource(request: QueryResource): Future[Long] = {
    val (parentFolderPath, name) = splitPath(request.uri)

    val dsFuture: Future[Option[Long]] = request.dataSource match {
      case Some(ds) => selectResource( ds.uri ).map( _.id ).map( Some(_) )
      case None => Future( None )
    }

    for {
      dsId            <- dsFuture
      parentFolderId  <- selectResourceFolder(parentFolderPath).map( _.id )
      resourceId      <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, JIResourceTypes.query, version = request.version + 1 ))
      queryResourceId <- insertQueryResource( JIQuery(request.language, request.query, dsId, resourceId) )
    } yield queryResourceId
  }

  def insertQueryResource(query: JIQuery): Future[Long] = {
    val action = queryResources += query
    dbContext.run(action).map( _ => query.id )
  }
}

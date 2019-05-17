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
import com.thing2x.rptsvr.QueryResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.lifted.ProvenShape

import scala.concurrent.Future

import DBSchema.profile.api._

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


trait JIQuerySupport { mySelf: DBRepository =>

  def selectQueryResourceModel(path: String): Future[QueryResource] = selectQueryResourceModel(Left(path))

  def selectQueryResourceModel(id: Long): Future[QueryResource] = selectQueryResourceModel(Right(id))

  private def selectQueryResourceModel(pathOrId: Either[String, Long]): Future[QueryResource] = {
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
        case Some(dsId) => selectDataSourceModel(dsId).map( Some( _ ) )
        case _ => Future( None )
      }

      dsFuture.map{ ds =>
        val fr = QueryResource(s"${f.uri}/${r.name}", r.label)
        fr.version = r.version
        fr.permissionMask = 1
        fr.query = q.sqlQuery
        fr.language = q.queryLanguage
        fr.dataSource = ds
        fr
      }
    }
  }

  def selectQueryResource(path: String): Future[JIQuery] = selectQueryResource(Left(path))

  def selectQueryResource(id: Long): Future[JIQuery] = selectQueryResource(Right(id))

  private def selectQueryResource(pathOrId: Either[String, Long]): Future[JIQuery] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter(_.uri === folderPath)
          resource <- resources.filter(_.parentFolder === folder.id).filter(_.name === name)
          query    <- queryResources.filter(_.id === resource.id)
        } yield query
      case Right(id) =>
        for {
          query    <- queryResources.filter(_.id === id)
        } yield query
    }
    dbContext.run(action.result.head)
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
      resourceId      <- insertResource( JIResource(name, parentFolderId, None, request.label, request.description, DBResourceTypes.query, version = request.version + 1 ))
      queryResourceId <- insertQueryResource( JIQuery(request.language, request.query, dsId, resourceId) )
    } yield queryResourceId
  }

  def insertQueryResource(query: JIQuery): Future[Long] = {
    val action = queryResources += query
    dbContext.run(action).map( _ => query.id )
  }
}

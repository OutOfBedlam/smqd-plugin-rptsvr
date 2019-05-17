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

import com.thing2x.rptsvr.InputControlResource
import slick.lifted.ProvenShape

import scala.concurrent.Future

// create table JIInputControl (
//        id number(19,0) not null,
//        type number(3,0),
//        mandatory number(1,0),
//        readOnly number(1,0),
//        visible number(1,0),
//        data_type number(19,0),
//        list_of_values number(19,0),
//        list_query number(19,0),
//        query_value_column nvarchar2(200),
//        defaultValue raw(255),
//        primary key (id)
//    );
final case class JIInputControl( controlType: Int,
                                 dataType: Option[Long],
                                 listOfValues: Option[Long],
                                 listQuery: Option[Long],
                                 queryValueColumn: Option[String],
                                 defaultValue: Option[Array[Byte]],
                                 mandatory: Boolean,
                                 readOnly: Boolean,
                                 visible: Boolean,
                                 id: Long = 0L)

trait JIInputControlSupport { mySelf: DBRepository =>
  import dbContext.profile.api._

  final class JIInputControlTable(tag: Tag) extends Table[JIInputControl](tag, "JIInputControl") {
    def controlType = column[Int]("type")
    def dataType = column[Option[Long]]("data_type")
    def listOfValues = column[Option[Long]]("list_of_values")
    def listQuery = column[Option[Long]]("list_query")
    def queryValueColumn = column[Option[String]]("query_value_column")
    def defaultValue = column[Option[Array[Byte]]]("defaultValue")
    def mandatory = column[Boolean]("mandatory")
    def readOnly = column[Boolean]("readOnly")
    def visible = column[Boolean]("visible")

    def id = column[Long]("id", O.PrimaryKey)

    def idFk = foreignKey("JIInputControl_id_fk", id, resources)(_.id)
    def dataTypeFk = foreignKey("JIInputControl_data_type_fk", dataType, dataTypes)(_.id.?)
    def listOfValuesFk = foreignKey("JIInputControl_list_of_values_fk", listOfValues, mySelf.listOfValues)(_.id.?)
    def listQueryFk = foreignKey("JIInputControl_list_query_fk", listQuery, queryResources)(_.id.?)

    def * : ProvenShape[JIInputControl] = (controlType, dataType, listOfValues, listQuery, queryValueColumn, defaultValue, mandatory, readOnly, visible, id).mapTo[JIInputControl]
  }


  def selectInputControlModel(path: String): Future[InputControlResource] = selectInputControlModel(Left(path))

  def selectInputControlModel(id: Long): Future[InputControlResource] = selectInputControlModel(Right(id))

  private def selectInputControlModel(pathOrId: Either[String, Long]): Future[InputControlResource] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter( _.uri === folderPath )
          resource <- resources.filter( _.name === name )
          ic       <- inputControls.filter( _.id === resource.id)
        } yield (ic, resource, folder)
      case Right(id) =>
        for {
          ic       <- inputControls.filter( _.id === id )
          resource <- ic.idFk
          folder   <- resource.parentFolderFk
        } yield (ic, resource, folder)
    }

    val result = for {
      (ic, resource, folder) <- dbContext.run(action.result.head)
      dt                     <- ic.dataType match {
        case Some(dataTypeId) => selectDataTypeModel(dataTypeId).map(Some(_))
        case _ => Future( None )
      }
      lov                    <- ic.listOfValues match {
        case Some(lovId) => Future( None) // TODO: add query
        case _ => Future(None)
      }
    } yield (ic, resource, folder, dt, lov)

    result map { case (ic, resource, folder, dt, lov) =>
      val fr = InputControlResource(s"${folder.uri}/${resource.name}", resource.name)
      fr.visible = ic.visible
      fr.readOnly = ic.readOnly
      fr.mandatory = ic.mandatory
      fr.dataType = dt
      fr.listOfValues = lov
      fr
    }
  }

  def selectInputControl(path: String): Future[JIInputControl] = selectInputControl(Left(path))

  def selectInputControl(id: Long): Future[JIInputControl] = selectInputControl(Right(id))

  private def selectInputControl(pathOrId: Either[String, Long]): Future[JIInputControl] = {
    val action = pathOrId match {
      case Left(path) =>
        val (folderPath, name) = splitPath(path)
        for {
          folder   <- resourceFolders.filter( _.uri === folderPath )
          resource <- resources.filter( _.parentFolder === folder.id ).filter( _.name === name )
          ic       <- inputControls.filter( _.id === resource.id)
        } yield ic
      case Right(id) =>
        for {
          ic       <- inputControls.filter( _.id === id )
        } yield ic
    }

    dbContext.run(action.result.head)
  }

  def insertInputControl(ctl: InputControlResource): Future[Long] = {
    val (folderPath, name) = splitPath(ctl.uri)

    val dtIdFuture = ctl.dataType match {
      case Some(dt) => insertDataType(dt).map( Some(_) )
      case _ => Future( None )
    }

    val lvIdFuture = ctl.listOfValues match {
      case Some(lv) => insertListOfValues(lv).map( Some(_) )
      case _ => Future ( None )
    }

    for {
      folder       <- selectResourceFolder(folderPath)
      dataTypeId   <- dtIdFuture
      lvId         <- lvIdFuture
      resourceId   <- insertResource( JIResource(name, folder.id, None, ctl.label, ctl.description, DBResourceTypes.inputControl, version = ctl.version + 1))
      _            <- insertInputControl( JIInputControl(ctl.controlType, dataTypeId, lvId, None, None, None, ctl.mandatory, ctl.readOnly, ctl.visible, resourceId) )
    } yield resourceId
  }

  def insertInputControl(ctl: JIInputControl): Future[Long] = {
    val action = inputControls += ctl
    dbContext.run(action).map( _ => ctl.id)
  }
}

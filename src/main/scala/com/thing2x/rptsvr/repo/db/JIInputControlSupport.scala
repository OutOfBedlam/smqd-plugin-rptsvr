package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.InputControlResource
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.jdbc.H2Profile.api._
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

  def idFk = foreignKey("jiinputcontrol_id_fk", id, resources)(_.id)
  def dataTypeFk = foreignKey("jiinputcontrol_data_type_fk", dataType, dataTypes)(_.id.?)
  def listOfValuesFk = foreignKey("jiinputcontrol_list_of_values_fk", listOfValues, DBSchema.listOfValues)(_.id.?)
  def listQueryFk = foreignKey("jiinputcontrol_list_query_fk", listQuery, queryResources)(_.id.?)

  def * : ProvenShape[JIInputControl] = (controlType, dataType, listOfValues, listQuery, queryValueColumn, defaultValue, mandatory, readOnly, visible, id).mapTo[JIInputControl]
}

trait JIInputControlSupport { mySelf: DBRepository =>

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
      _            <- insertInputControl( JIInputControl(ctl.controlType, dataTypeId, lvId, None, None, None, ctl.mandatory, ctl.readOnly, ctl.visible) )
    } yield resourceId
  }

  def insertInputControl(ctl: JIInputControl): Future[Long] = {
    val action = inputControls += ctl
    dbContext.run(action).map( _ => ctl.id)
  }
}

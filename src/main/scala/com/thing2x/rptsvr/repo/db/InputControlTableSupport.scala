package com.thing2x.rptsvr.repo.db

import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape


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

  def * : ProvenShape[JIInputControl] = (controlType, dataType, listOfValues, listQuery, queryValueColumn, defaultValue, mandatory, readOnly, visible, id).mapTo[JIInputControl]
}

trait InputControlTableSupport {

}

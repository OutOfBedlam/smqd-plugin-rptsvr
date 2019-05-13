package com.thing2x.rptsvr.repo.db

import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape

//    create table JIDataType (
//        id number(19,0) not null,
//        type number(3,0),
//        maxLength number(10,0),
//        decimals number(10,0),
//        regularExpr nvarchar2(255),
//        minValue raw(1000),
//        max_value raw(1000),
//        strictMin number(1,0),
//        strictMax number(1,0),
//        primary key (id)
//    );
final case class JIDataType( dataType: Int,
                             maxLength: Option[Long],
                             decimals: Option[Long],
                             regularExpr: Option[String],
                             minValue: Option[Array[Byte]],
                             maxValue: Option[Array[Byte]],
                             strictMin: Boolean,
                             strictMax: Boolean,
                             id: Long = 0L)

final class JIDataTypeTable(tag: Tag) extends Table[JIDataType](tag, "JIDataType") {
  def dataType = column[Int]("type")
  def maxLength = column[Option[Long]]("maxLength")
  def decimals = column[Option[Long]]("decimals")
  def regularExpr = column[Option[String]]("regularExpr")
  def minValue = column[Option[Array[Byte]]]("minValue")
  def maxValue = column[Option[Array[Byte]]]("max_value")
  def strictMin = column[Boolean]("strictMin")
  def strictMax = column[Boolean]("strictMax")
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def * : ProvenShape[JIDataType] = (dataType, maxLength, decimals, regularExpr, minValue, maxValue, strictMin, strictMax, id).mapTo[JIDataType]
}

trait DataTypeTableSupport {

}

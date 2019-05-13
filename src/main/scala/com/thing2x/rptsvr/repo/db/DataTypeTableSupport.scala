package com.thing2x.rptsvr.repo.db

import java.util.Base64

import com.thing2x.rptsvr.DataTypeResource
import slick.jdbc.H2Profile.api._
import com.thing2x.rptsvr.repo.db.DBSchema._
import slick.lifted.ProvenShape

import scala.concurrent.Future

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
                             maxLength: Option[Int],
                             decimals: Option[Int],
                             regularExpr: Option[String],
                             minValue: Option[Array[Byte]],
                             maxValue: Option[Array[Byte]],
                             strictMin: Boolean,
                             strictMax: Boolean,
                             id: Long = 0L)

final class JIDataTypeTable(tag: Tag) extends Table[JIDataType](tag, "JIDataType") {
  def dataType = column[Int]("type")
  def maxLength = column[Option[Int]]("maxLength")
  def decimals = column[Option[Int]]("decimals")
  def regularExpr = column[Option[String]]("regularExpr")
  def minValue = column[Option[Array[Byte]]]("minValue")
  def maxValue = column[Option[Array[Byte]]]("max_value")
  def strictMin = column[Boolean]("strictMin")
  def strictMax = column[Boolean]("strictMax")
  def id = column[Long]("id", O.PrimaryKey)

  def * : ProvenShape[JIDataType] = (dataType, maxLength, decimals, regularExpr, minValue, maxValue, strictMin, strictMax, id).mapTo[JIDataType]
}

trait DataTypeTableSupport { mySelf: DBRepository =>

  def insertDataType(dt: DataTypeResource): Future[Long] = {
    val (folderPath, name) = splitPath(dt.uri)
    val minValue = dt.minValue.map( d => Base64.getDecoder.decode(d) )
    val maxValue = dt.maxValue.map( d => Base64.getDecoder.decode(d) )
    for {
      folderId <- selectResourceFolder(folderPath).map( _.id )
      resourceId <- insertResource( JIResource(name, folderId, None, dt.label, dt.description, JIResourceTypes.dataType, version = dt.version + 1))
      dtId       <- insertDataType( JIDataType(dt.typeId, dt.maxLength, dt.decimals, dt.regularExpr, minValue, maxValue, dt.strictMin, dt.strictMax, resourceId) )
    } yield dtId
  }

  def insertDataType(dt: JIDataType): Future[Long] = {
    val action = dataTypes += dt
    dbContext.run(action).map( _ => dt.id)
  }
}

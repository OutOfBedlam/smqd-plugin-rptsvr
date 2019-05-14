package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.ListOfValuesResource
import slick.lifted.ProvenShape

import scala.concurrent.Future

import DBSchema.profile.api._

//    create table JIListOfValues (
//        id number(19,0) not null,
//        primary key (id)
//    );
final case class JIListOfValues(id: Long = 0L)

final class JIListOfValuesTable(tag: Tag) extends Table[JIListOfValues](tag, "JIListOfValues") {
  def id = column[Long]("id", O.PrimaryKey)

  def idFk = foreignKey("jilistofvalues_id_fk", id, DBSchema.resources)(_.id)

  def * : ProvenShape[JIListOfValues] = id.mapTo[JIListOfValues]
}

//
//    create table JIListOfValuesItem (
//        id number(19,0) not null,
//        label nvarchar2(255),
//        value blob,
//        idx number(10,0) not null,
//        primary key (id, idx)
//    );
final case class JIListOfValuesItem( label: Option[String],
                                     value: Option[Array[Byte]],
                                     idx: Int,
                                     id: Long = 0L)

final class JIListOfValueItemTable(tag: Tag) extends Table[JIListOfValuesItem](tag, "JIListOfValuesItem") {
  def label = column[Option[String]]("label")
  def value = column[Option[Array[Byte]]]("value")
  def idx = column[Int]("idx")
  def id = column[Long]("id", O.PrimaryKey)

  def pk = primaryKey("jilistofvaluesitem_pk", (id, idx))
  def idFk = foreignKey("jilistofvaluesitem_id_fk", id, DBSchema.listOfValues)(_.id)

  def * : ProvenShape[JIListOfValuesItem] = (label, value, idx, id).mapTo[JIListOfValuesItem]
}

trait JIListOfValuesSupport { mySelf: DBRepository =>

  def insertListOfValues(lv: ListOfValuesResource): Future[Long] = {
    ???
  }

  def insertListOfValues(lv: JIListOfValues): Future[Long] = ???
}

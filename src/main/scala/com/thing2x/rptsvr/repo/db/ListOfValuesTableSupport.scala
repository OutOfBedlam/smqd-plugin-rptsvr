package com.thing2x.rptsvr.repo.db

import com.thing2x.rptsvr.ListOfValuesResource

import scala.concurrent.Future

//    create table JIListOfValues (
//        id number(19,0) not null,
//        primary key (id)
//    );
final case class JIListOfValues(id: Long = 0L)

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

trait ListOfValuesTableSupport { mySelf: DBRepository =>

  def insertListOfValues(lv: ListOfValuesResource): Future[Long] = {
    ???
  }

  def insertListOfValues(lv: JIListOfValues): Future[Long] = ???
}

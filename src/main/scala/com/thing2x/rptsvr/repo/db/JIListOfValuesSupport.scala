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

import com.thing2x.rptsvr.ListOfValuesResource
import slick.lifted.ProvenShape

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

trait JIListOfValuesSupport { mySelf: DBRepository =>
  import dbContext.profile.api._


  final class JIListOfValuesTable(tag: Tag) extends Table[JIListOfValues](tag, dbContext.table("JIListOfValues")) {
    def id = column[Long]("ID", O.PrimaryKey)

    def idFk = foreignKey("JILISTOFVALUES_ID_FK", id, resources)(_.id)

    def * : ProvenShape[JIListOfValues] = id.mapTo[JIListOfValues]
  }

  final class JIListOfValueItemTable(tag: Tag) extends Table[JIListOfValuesItem](tag, "JIListOfValuesItem") {
    def label = column[Option[String]]("LABEL")
    def value = column[Option[Array[Byte]]]("VALUE")
    def idx = column[Int]("IDX")
    def id = column[Long]("ID", O.PrimaryKey)

    def pk = primaryKey("JILISTOFVALUESITEM_PK", (id, idx))
    def idFk = foreignKey("JILISTOFVALUESITEM_ID_FK", id, listOfValues)(_.id)

    def * : ProvenShape[JIListOfValuesItem] = (label, value, idx, id).mapTo[JIListOfValuesItem]
  }

  def insertListOfValues(lv: ListOfValuesResource): Future[Long] = {
    ???
  }

  def insertListOfValues(lv: JIListOfValues): Future[Long] = ???
}

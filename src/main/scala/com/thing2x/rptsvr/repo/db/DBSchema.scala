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


import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

trait DBSchema extends JIResourceFolderSupport
  with JIResourceSupport
  with JIFileResourceSupport
  with JIDataSourceSupport
  with JIInputControlSupport
  with JIInputControlQueryColumnSupport
  with JIQuerySupport
  with JIDataTypeSupport
  with JIListOfValuesSupport
  with JIReportUnitSupport
  with JIReportUnitInputControlSupport
  with JIReportUnitResourceSupport { mySelf: DBRepository =>

  import dbContext.profile.api._

  def createSchema(ctx: DBRepositoryContext): Unit = {
    val schema = Seq(
      resourceFolders.schema,
      resources.schema,
      fileResources.schema,
      jdbcResources.schema,
      queryResources.schema,
      dataTypes.schema,
      listOfValues.schema,
      inputControls.schema,
      inputControlQueryColumns.schema,
      reportUnits.schema,
      reportUnitResources.schema,
      reportUnitInputControls.schema,
    )

    val resourceFolderInsert = resourceFolders returning resourceFolders.map(_.id)
    implicit val ec: ExecutionContext = ctx.executionContext
    val setup = for {
      // create tables
      _                <- schema.reduce( _ ++ _).create
      // insert default tables
      rootFolderId     <- resourceFolderInsert += JIResourceFolder("/", "/", "/", Some("Root"), -1)
      orgId            <- resourceFolderInsert += JIResourceFolder("/organizations", "organizations", "Organizations", Some("Oraganizations"), rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/public", "public", "Public", None, rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/temp", "temp", "Temp", Some("Temp"), rootFolderId)
      _                <- resourceFolderInsert += JIResourceFolder("/organizations/organization_1", "organization_1", "Organization", None, orgId)
    } yield rootFolderId

    ctx.runSync(setup, 8.second)
  }

}

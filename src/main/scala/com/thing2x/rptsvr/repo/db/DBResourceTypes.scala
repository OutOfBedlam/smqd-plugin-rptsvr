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

object DBResourceTypes {
  val file = "com.jaspersoft.jasperserver.api.metadata.common.domain.FileResource"
  val reportUnit = "com.jaspersoft.jasperserver.api.metadata.jasperreports.domain.ReportUnit"

  val jdbcDataSource = "com.jaspersoft.jasperserver.api.metadata.jasperreports.domain.JdbcReportDataSource"
  val customDataSource = "com.jaspersoft.jasperserver.api.metadata.jasperreports.domain.CustomReportDataSource"
  val jndiJdbcDataSource = "com.jaspersoft.jasperserver.api.metadata.jasperreports.domain.JndiJdbcReportDataSource"
  val semanticDataLayerDataSource = "com.jaspersoft.commons.semantic.datasource.SemanticLayerDataSource"

  val inputControl = "com.jaspersoft.jasperserver.api.metadata.common.domain.InputControl"
  val listValues = "com.jaspersoft.jasperserver.api.metadata.common.domain.ListOfValues"
  val query = "com.jaspersoft.jasperserver.api.metadata.common.domain.Query"
  val adhocDataView = "com.jaspersoft.ji.adhoc.AdhocDataView"
  val dataType = "com.jaspersoft.jasperserver.api.metadata.common.domain.DataType"
  val olapUnit = "com.jaspersoft.jasperserver.api.metadata.olap.domain.OlapUnit"
  val securMondrianConnection = "com.jaspersoft.ji.ja.security.domain.SecureMondrianConnection"
}

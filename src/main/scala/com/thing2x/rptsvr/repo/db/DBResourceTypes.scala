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

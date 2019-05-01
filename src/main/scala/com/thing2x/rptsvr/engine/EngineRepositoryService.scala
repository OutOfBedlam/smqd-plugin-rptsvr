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

package com.thing2x.rptsvr.engine

import java.io.{InputStream, OutputStream}

import com.thing2x.rptsvr.{Repository => BackendRepo}
import net.sf.jasperreports.engine.JasperReportsContext
import net.sf.jasperreports.repo._

class EngineRepositoryService(jsContext: JasperReportsContext, backend: BackendRepo) extends StreamRepositoryService {
  override def getInputStream(uri: String): InputStream = ???

  override def getOutputStream(uri: String): OutputStream = ???

  override def getResource(uri: String): Resource = ???

  override def saveResource(uri: String, resource: Resource): Unit = ???

  override def getResource[K <: Resource](uri: String, resourceType: Class[K]): K = {
    val repositoryContext = SimpleRepositoryContext.of(jsContext)
    val persistenceService = PersistenceUtil.getInstance(jsContext).getService(classOf[EngineRepositoryService], resourceType)
    if (persistenceService != null)
    {
      //println(s"********************** $uri")
      val is = repositoryContext.getJasperReportsContext.getValue(uri).asInstanceOf[InputStream]
      if (is != null) {
        val isr = new InputStreamResource
        isr.setInputStream(is)
        return isr.asInstanceOf[K]
      }
    }

    null.asInstanceOf[K]
  }
}

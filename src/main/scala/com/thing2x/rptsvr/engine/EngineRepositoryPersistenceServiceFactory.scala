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

import net.sf.jasperreports.engine.JasperReportsContext
import net.sf.jasperreports.repo._

object EngineRepositoryPersistenceServiceFactory extends PersistenceServiceFactory {
  override def getPersistenceService[K <: RepositoryService, L <: Resource](
    jasperReportsContext: JasperReportsContext,
    repositoryServiceType: Class[K],
    resourceType: Class[L]): PersistenceService = {

    if (classOf[EngineRepositoryService].isAssignableFrom(repositoryServiceType)) {
      if (classOf[InputStreamResource].getName.equals(resourceType.getName))
      {
        return new InputStreamPersistenceService
      }
      else if (classOf[OutputStreamResource].getName.equals(resourceType.getName))
      {
        return new OutputStreamPersistenceService
      }
      else if (classOf[ReportResource].getName.equals(resourceType.getName))
      {
        return new SerializedReportPersistenceService
      }
      else if (classOf[ResourceBundleResource].getName.equals(resourceType.getName))
      {
        return new ResourceBundlePersistenceService(jasperReportsContext)
      }
      else if (classOf[DataAdapterResource].isAssignableFrom(resourceType))
      {
        return new CastorDataAdapterPersistenceService(jasperReportsContext)
      }
      else if (classOf[CastorResource[_]].isAssignableFrom(resourceType))
      {
        return new CastorObjectPersistenceService(jasperReportsContext)
      }
      else if (classOf[SerializableResource[_]].isAssignableFrom(resourceType))
      {
        return new SerializedObjectPersistenceService()
      }
    }

    null
  }
}

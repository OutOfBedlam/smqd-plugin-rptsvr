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

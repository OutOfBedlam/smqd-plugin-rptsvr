package com.thing2x.rptsvr.repo

import scala.collection.mutable
import scala.language.implicitConversions

package object fs {

  private[fs] val METAFILENAME = "fr_meta.conf"
  private[fs] val CONTENTFILENAME = "content.data"

  private[fs] object MetaKey{
    def apply[T](name: String) = new MetaKey[T](name)
  }

  private[fs] class MetaKey[T](val name: String){
    def apply: String = name
    def apply(value: T): MetaKeyValue[T] = new MetaKeyValue(this, value)

    // get value from map by the key
    def :: (map: mutable.Map[String, Any]): T = map(name).asInstanceOf[T]
    // check if the key exists in the map
    def ?: (map: mutable.Map[String, Any]): Boolean = map.contains(name)
  }

  private[fs] class MetaKeyValue[T](val key: MetaKey[T], val value: T) {
    def apply: (String, T) = (key.name, value)

    // update the meta info
    def =: (map: mutable.Map[String, Any]): Unit = map(key.name) = value
    // update the meta info if the key does not already exist
    def ~: (map: mutable.Map[String, Any]): Unit = {
      if (!map.contains(key.name))
        map(key.name) = value
    }
  }

  private[fs] implicit def metaKeyToString(mk: MetaKey[_]): String = mk.name
  private[fs] implicit def metaKeyValueToTuple[T](mkv: MetaKeyValue[T]): (String, T) = (mkv.key.name, mkv.value)

  private[fs] val META_URI: MetaKey[String] = MetaKey("uri")
  private[fs] val META_VERSION: MetaKey[Int] = MetaKey("version")
  private[fs] val META_TYPE: MetaKey[String] = MetaKey("type")
  private[fs] val META_LABEL: MetaKey[String] = MetaKey("label")
  private[fs] val META_PERMISSIONMASK: MetaKey[Int] = MetaKey("permissionMask")
  private[fs] val META_CREATIONTIME: MetaKey[Long] = MetaKey("creationTime")
  private[fs] val META_UPDATETIME: MetaKey[Long] = MetaKey("updateTime")
  private[fs] val META_DESCRIPTION: MetaKey[String] = MetaKey("description")
}

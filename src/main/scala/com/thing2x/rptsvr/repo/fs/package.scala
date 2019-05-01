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
  }

  private[fs] class MetaKeyValue[T](val key: MetaKey[T], val value: T) {
    def apply: (String, T) = (key.name, value)
  }

  private[fs] implicit def metaKeyToString(mk: MetaKey[_]): String = mk.name
  private[fs] implicit def metaKeyValueToTuple[T](mkv: MetaKeyValue[T]): (String, T) = (mkv.key.name, mkv.value)

  // common attributes
  private[fs] val META_URI: MetaKey[String] = MetaKey("uri")
  private[fs] val META_VERSION: MetaKey[Int] = MetaKey("version")
  private[fs] val META_LABEL: MetaKey[String] = MetaKey("label")
  private[fs] val META_PERMISSIONMASK: MetaKey[Int] = MetaKey("permissionMask")
  private[fs] val META_CREATIONDATE: MetaKey[Long] = MetaKey("creationDate")
  private[fs] val META_UPDATEDATE: MetaKey[Long] = MetaKey("updateDate")
  private[fs] val META_DESCRIPTION: MetaKey[String] = MetaKey("description")

  // internal attributes
  private[fs] val META_RESOURCETYPE: MetaKey[String] = MetaKey("resourceType")
  private[fs] val META_CREATIONTIME: MetaKey[Long] = MetaKey("creationTime")
  private[fs] val META_UPDATETIME: MetaKey[Long] = MetaKey("updateTime")

  // File Resource attributes
  private[fs] val META_FILETYPE: MetaKey[String] = MetaKey("type")
  private[fs] val META_CONTENT: MetaKey[String] = MetaKey("content")

  // Report Unit Resource attributes
  private[fs] val META_ALWAYSPROMPTCONTROLS: MetaKey[Boolean] = MetaKey("alwaysPromptControls")
  private[fs] val META_CONTROLRAYOUT: MetaKey[String] = MetaKey("controlsLayout")

}

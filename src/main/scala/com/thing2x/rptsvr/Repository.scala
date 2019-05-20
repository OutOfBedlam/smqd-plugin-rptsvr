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

package com.thing2x.rptsvr

import scala.concurrent.Future

object Repository {
  private var _instance: Option[Repository] = None
  def instance: Option[Repository] = _instance

  class RepositoryException extends Exception

  class ResourceNotFoundException(uri: String) extends Exception
  class ResourceAlreadyExistsExeption(uri: String) extends Exception
}

trait Repository {

  // the lastest created repository instance always win
  Repository._instance = Some(this)

  val context: RepositoryContext
  def listFolder(path: String, recursive: Boolean, sortBy: String, limit: Int): Future[ListResult[Resource]]

  def setResource(path: String, request: Resource, createFolders: Boolean, overwrite: Boolean): Future[Result[Resource]]
  def getResource(path: String, isReferenced: Boolean = false): Future[Result[Resource]]

  def getContent(path: String): Future[Either[Throwable, FileContent]]

  def deleteResource(path: String): Future[Boolean]

  private val hexChars: Array[Char] = Array( '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' )

  protected def hexify(data: Array[Byte]): String = {
    val sb = StringBuilder.newBuilder

    data.foreach { ch =>
      val highBits = (ch.toInt & 0x000000F0) >> 4
      val lowBits = ch.toInt & 0x0000000F
      sb.append(hexChars(highBits)).append(hexChars(lowBits))
    }

    sb.toString
  }

  protected def dehexify(data: String): Array[Byte] = {
    val bytes = new Array[Byte](data.length/2)
    data.grouped(2).zipWithIndex.foreach { case(ch, idx) =>
      bytes(idx) = Integer.parseInt(ch, 16).toByte
    }
    bytes
  }
}

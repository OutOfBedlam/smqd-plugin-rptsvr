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

package com.thing2x

import scala.language.implicitConversions

package object rptsvr {
  type Result[+T <: Resource] = Either[Throwable, T]

  implicit def asRightFromResource[T <: Resource](resource: T): Result[T] = Right(resource)
  implicit def asLeftFromResource[T <: Resource](exception: Throwable): Result[T] = Left(exception)

  type ListResult[+T <: Resource] = Either[Throwable, Seq[T]]

  implicit def asRightFromList[T <: Resource](list: Seq[T]): ListResult[T] = Right(list)
  implicit def asLeftFromList[T <: Resource](exception: Throwable): ListResult[T] = Left(exception)
}

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

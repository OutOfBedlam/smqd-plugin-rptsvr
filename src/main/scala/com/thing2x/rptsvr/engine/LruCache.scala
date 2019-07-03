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

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object LruCache {
  def apply[K, V](maxEntries: Int = 500, dropFraction: Double = 0.20, ttl: Duration = 5.minutes)(implicit ec: ExecutionContext) =
    new LruCache[K, V](maxEntries, dropFraction, ttl)
}

class LruCache[K, V](val maxEntries: Int, val dropFraction: Double, val ttl: Duration)(implicit ec: ExecutionContext) {

  private val store = new Store

  def get(key: K): Option[Future[V]] = synchronized {
    store.getEntry(key).map(_.future)
  }

  def fromFuture(key: K, timestamp: Option[Long] = None)(future: => Future[V]): Future[V] = synchronized {
    store.getEntry(key) match {
      case Some(entry) if (timestamp.isDefined && entry.enlistTime > timestamp.get) || timestamp.isEmpty =>
        // Use cached value
        //   if given timestamp is prior than enlist time
        //   or timestamp is not given
        entry.future
      case _ =>
        val fut = Future{ for ( r <- future ) yield r }.flatten
        store.setEntry(key, new StoreEntry(fut))
        if (!fut.isCompleted) {
          fut.onComplete {
            case Success(_) => // nothing to do
            case Failure(_) => this.synchronized {
              store.remove(key) // remove entry and try it later
            }
          }
        }
        fut
    }
  }

  private class StoreEntry(val future: Future[V]) {
    val enlistTime: Long = System.currentTimeMillis
    private var lastUsedTime = System.currentTimeMillis
    def refresh(): Unit = lastUsedTime = System.currentTimeMillis
    def isAlive: Boolean = (System.currentTimeMillis - lastUsedTime).millis < ttl

    override def toString: String = future.value match {
      case Some(Success(value)) => value.toString
      case Some(Failure(ex)) => ex.toString
      case None => "pending"
    }
  }

  private class Store extends mutable.LinkedHashMap[K, StoreEntry] {
    def getEntry(key: K): Option[LruCache.this.StoreEntry] = {
      get(key).flatMap { entry =>
        if (entry.isAlive) {
          entry.refresh() // TODO: optimize refresh()
          remove(key)
          put(key, entry)
          Some(entry)
        }
        else {
          // entry expired, remove all earlier entries
          while (firstEntry.key != key) remove(firstEntry.key)
          remove(key)
          None
        }
      }
    }

    def setEntry(key: K, entry: StoreEntry): Unit = {
      put(key, entry)
      if (size > maxEntries) {
        val newSize = maxEntries - (maxEntries * dropFraction).toInt
        while( size > newSize) remove(firstEntry.key)
      }
    }
  }
}

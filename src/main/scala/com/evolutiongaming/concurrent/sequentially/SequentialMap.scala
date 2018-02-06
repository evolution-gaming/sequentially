package com.evolutiongaming.concurrent.sequentially

import com.evolutiongaming.concurrent.sequentially.TrieMapHelper._

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

trait SequentialMap[K, V] {

  type Opt = Option[V]

  type Directive = MapDirective[V]

  def values: collection.Map[K, V]

  def update[T](key: K)(f: Opt => (Directive, T)): Future[T]

  def put(key: K, value: V): Future[Opt] = {
    update(key) { oldValue =>
      val directive = MapDirective.update(value)
      (directive, oldValue)
    }
  }

  def remove(key: K): Future[Opt] = {
    update(key) { oldValue =>
      val directive = MapDirective.remove
      (directive, oldValue)
    }
  }

  def get(key: K): Option[V] = values.get(key)

  override def toString: String = s"SequentialMap${ values mkString "," }"
}

object SequentialMap {

  def apply[K, V](
    sequentially: Sequentially[K],
    map: TrieMap[K, V] = TrieMap.empty[K, V]): SequentialMap[K, V] = {

    new SequentialMap[K, V] {

      def values: collection.Map[K, V] = map

      def update[T](key: K)(f: Opt => (Directive, T)): Future[T] = {

        sequentially(key) {
          val value = map.get(key)
          val (directive, result) = f(value)
          map.apply(key, directive)
          result
        }
      }
    }
  }
}
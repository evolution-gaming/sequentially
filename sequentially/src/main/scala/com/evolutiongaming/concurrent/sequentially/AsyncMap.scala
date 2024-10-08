package com.evolutiongaming.concurrent.sequentially

import com.evolutiongaming.concurrent.FutureHelper.*
import com.evolutiongaming.concurrent.sequentially.TrieMapHelper.*

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

trait AsyncMap[K, V] extends SequentialMap[K, V] {

  def updateAsync[T](key: K)(f: Opt => Future[(Directive, T)]): Future[T]

  override def toString: String = s"AsyncMap${ values mkString "," }"
}

object AsyncMap {

  def apply[K, V](
    sequentially: SequentiallyAsync[K],
    map: TrieMap[K, V] = TrieMap.empty[K, V],
  ): AsyncMap[K, V] = {

    implicit val ec: ExecutionContext = ExecutionContext.parasitic

    new AsyncMap[K, V] {

      def values: TrieMap[K, V] = map

      def updateAsync[T](key: K)(f: Opt => Future[(Directive, T)]): Future[T] = {
        def task: Future[T] = f(map.get(key)) map { case (directive, result) =>
          map.apply(key, directive)
          result
        }

        sequentially.async(key)(task)
      }

      def update[T](key: K)(f: Opt => (Directive, T)): Future[T] = {
        updateAsync(key) { value => f(value).future }
      }
    }
  }
}

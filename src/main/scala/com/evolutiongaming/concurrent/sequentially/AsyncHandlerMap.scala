package com.evolutiongaming.concurrent.sequentially

import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.concurrent.sequentially.TrieMapHelper._

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

trait AsyncHandlerMap[K, V] extends AsyncMap[K, V] {
  
  def updateHandler[T](key: K)(f: Opt => Future[Opt => Future[(Directive, T)]]): Future[T]

  override def toString: String = s"AsyncHandlerMap${ values mkString "," }"
}

object AsyncHandlerMap {

  def apply[K, V](
    sequentially: SequentiallyHandler[K],
    map: TrieMap[K, V] = TrieMap.empty[K, V]): AsyncHandlerMap[K, V] = {

    implicit val ec = CurrentThreadExecutionContext

    new AsyncHandlerMap[K, V] {

      def values = map

      def updateHandler[T](key: K)(f: Opt => Future[Opt => Future[(Directive, T)]]) = {

        def value() = map.get(key)

        def task = f(value()) map { task =>
          () =>
            task(value()) map { case (directive, result) =>
              map.apply(key, directive)
              result
            }
        }

        sequentially.handler(key)(task)
      }

      def updateAsync[T](key: K)(f: Opt => Future[(Directive, T)]): Future[T] = {
        updateHandler(key) { _ => Future.successful(f) }
      }

      def update[T](key: K)(f: Opt => (Directive, T)): Future[T] = {
        updateAsync(key) { value => Future.successful(f(value)) }
      }
    }
  }
}

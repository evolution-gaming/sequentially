package com.evolutiongaming.concurrent.sequentially

import com.evolutiongaming.concurrent.FutureHelper.*
import com.evolutiongaming.concurrent.sequentially.TrieMapHelper.*

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

trait AsyncHandlerMap[K, V] extends AsyncMap[K, V] {

  def updateHandler[T](key: K)(f: Opt => Future[Opt => Future[(Directive, T)]]): Future[T]

  override def toString: String = s"AsyncHandlerMap${ values mkString "," }"
}

object AsyncHandlerMap {

  def apply[K, V](
    sequentially: SequentiallyHandler[K],
    map: TrieMap[K, V] = TrieMap.empty[K, V],
  ): AsyncHandlerMap[K, V] = {

    implicit val ec: ExecutionContext = ExecutionContext.parasitic

    new AsyncHandlerMap[K, V] {

      def values: TrieMap[K, V] = map

      def updateHandler[T](key: K)(f: Opt => Future[Opt => Future[(Directive, T)]]): Future[T] = {

        def value(): Option[V] = map.get(key)

        def task: Future[() => Future[T]] = f(value()) map { task => () =>
          task(value()) map { case (directive, result) =>
            map.apply(key, directive)
            result
          }
        }

        sequentially.handler(key)(task)
      }

      def updateAsync[T](key: K)(f: Opt => Future[(Directive, T)]): Future[T] = {
        updateHandler(key) { _ => f.future }
      }

      def update[T](key: K)(f: Opt => (Directive, T)): Future[T] = {
        updateAsync(key) { value => f(value).future }
      }
    }
  }
}

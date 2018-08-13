package com.evolutiongaming.concurrent.sequentially

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import com.evolutiongaming.concurrent.FutureHelper._
import com.evolutiongaming.concurrent.sequentially.Sequentially.{BufferSize, Substreams}
import com.evolutiongaming.concurrent.sequentially.SourceQueueHelper._
import com.evolutiongaming.concurrent.{AvailableProcessors, CurrentThreadExecutionContext}

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

/**
  * Runs tasks sequentially for the same key and in parallel - for different keys
  */
trait SequentiallyHandler[-K] extends SequentiallyAsync[K] {

  def handler[KK <: K, T](key: KK)(task: => Future[() => Future[T]]): Future[T]

  def async[KK <: K, T](key: K)(task: => Future[T]): Future[T] = {
    handler(key) {
      (() => task).future
    }
  }
}

object SequentiallyHandler {

  lazy val Parallelism: Int = AvailableProcessors() * 10

  def apply[K](
    substreams: Int = Substreams,
    parallelism: Int = Parallelism,
    bufferSize: Int = BufferSize,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure)
    (implicit materializer: Materializer): SequentiallyHandler[K] = {

    val pf: PartialFunction[Throwable, Unit] = { case _ => () }
    val pff: PartialFunction[Throwable, () => Future[Unit]] = { case _ => () => Future.unit }

    val queue = Source
      .queue[Elem](bufferSize, overflowStrategy)
      .groupBy(substreams, _.substream)
      .buffer(bufferSize, OverflowStrategy.backpressure)
      .mapAsync(parallelism) { _.apply() }
      .mapAsync(1) { _.apply() }
      .mergeSubstreams
      .to(Sink.ignore)
      .run()

    implicit val ec = materializer.executionContext

    case class Elem(substream: Int, apply: () => Future[() => Future[Any]])

    new SequentiallyHandler[K] {

      def handler[KK <: K, T](key: KK)(task: => Future[() => Future[T]]): Future[T] = {
        val substream = Substream(key, substreams)
        val promise = Promise[T]

        val safeTask = () => {
          val safeTask = () => task.map { task =>
            () => {
              val future = Future(task()).flatten
              promise.completeWith(future)
              future.recover[Any](pf)
            }
          }

          val future = Future(safeTask()).flatten
          future.failed.foreach { failure => promise.failure(failure) }
          future.recover(pff)
        }

        val elem = Elem(substream, safeTask)

        for {
          _ <- queue.offerOrError(elem, s"$key failed to enqueue task")
          result <- promise.future
        } yield result
      }
    }
  }


  def now[T]: SequentiallyHandler[T] = Now


  private object Now extends SequentiallyHandler[Any] {

    private implicit val ec = CurrentThreadExecutionContext

    def handler[KK <: Any, T](key: KK)(task: => Future[() => Future[T]]): Future[T] = {
      try {
        task.flatMap { _.apply() }
      } catch {
        case NonFatal(failure) => Future.failed(failure)
      }
    }
  }
}

package com.evolutiongaming.concurrent.sequentially

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
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
      Future.successful(() => task)
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

    case class Elem(substream: Int, apply: () => Future[() => Future[Unit]])

    implicit val ec = CurrentThreadExecutionContext

    val queue = Source
      .queue[Elem](bufferSize, overflowStrategy)
      .groupBy(substreams, _.substream)
      .buffer(bufferSize, OverflowStrategy.backpressure)
      .mapAsync(parallelism) { _.apply() }
      .mapAsync(1) { _.apply() }
      .to(Sink.ignore)
      .run()

    new SequentiallyHandler[K] {

      def handler[KK <: K, T](key: KK)(task: => Future[() => Future[T]]): Future[T] = {
        val substream = Substream(key, substreams)
        val promise = Promise[T]

        def safe[A](fallback: => A)(task: => Future[A]): Future[A] = {
          val result = try task catch { case NonFatal(failure) => Future.failed(failure) }
          result recover { case failure =>
            promise.failure(failure)
            fallback
          }
        }

        val safeTask = () => safe(() => Future.successful(())) {
          task map { task =>
            () =>
              safe(()) {
                task() map { result => promise.success(result); () }
              }
          }
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

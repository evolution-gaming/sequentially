package com.evolutiongaming.concurrent

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import com.evolutiongaming.concurrent.sequentially.SequentiallyF

import scala.concurrent.Future

/** Wrapper around SequentiallyF that adds metrics tracking.
  * Since SequentiallyF is a final class, we wrap it by delegation.
  */
final class MeteredSequentiallyF[F[_]: Async, -K] private (
  private val sequentially: SequentiallyF[F, K],
  private val metrics: SequentiallyMetricsF[F],
) {

  def apply[T](
    key: K
  )(
    task: => T
  )(implicit
    dispatcher: Dispatcher[F]
  ): Future[T] = {
    dispatcher.unsafeToFuture(applyF(key)(Async[F].delay(task)))
  }

  def applyF[T](key: K)(task: => F[T]): F[T] = {
    val start = System.nanoTime()
    
    metrics.queue(start) *> metrics.run(sequentially.applyF(key)(task))
  }
}

object MeteredSequentiallyF {

  def apply[F[_]: Async, K](
    sequentially: SequentiallyF[F, K],
    name: String,
    sequentiallyMetrics: SequentiallyMetricsF.Factory[F],
  ): MeteredSequentiallyF[F, K] = {
    apply(sequentially, sequentiallyMetrics(name))
  }

  def apply[F[_]: Async, K](
    sequentially: SequentiallyF[F, K],
    metrics: SequentiallyMetricsF[F],
  ): MeteredSequentiallyF[F, K] = {
    new MeteredSequentiallyF(sequentially, metrics)
  }

  trait Factory[F[_]] {
    def apply[K](name: String): MeteredSequentiallyF[F, K]
  }

  object Factory {

    trait Provider[F[_]] {
      def apply[K]: SequentiallyF[F, K]
    }

    def apply[F[_]: Async](
      provider: Provider[F],
      sequentiallyMetrics: SequentiallyMetricsF.Factory[F],
    ): Factory[F] = new Factory[F] {
      override def apply[K](name: String): MeteredSequentiallyF[F, K] =
        MeteredSequentiallyF(provider.apply[K], sequentiallyMetrics(name))
    }
  }

}

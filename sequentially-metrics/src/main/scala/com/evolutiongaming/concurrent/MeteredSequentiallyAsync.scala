package com.evolutiongaming.concurrent

import com.evolutiongaming.concurrent.sequentially.SequentiallyAsync

import scala.concurrent.Future

object MeteredSequentiallyAsync {

  def apply[K](
    sequentially: SequentiallyAsync[K],
    name: String,
    sequentiallyMetrics: SequentiallyMetrics.Factory,
  ): SequentiallyAsync[K] = {
    apply(sequentially, sequentiallyMetrics(name))
  }

  def apply[K](
    sequentially: SequentiallyAsync[K],
    metrics: SequentiallyMetrics,
  ): SequentiallyAsync[K] =
    new SequentiallyAsync[K] {
      def async[KK <: K, T](key: K)(task: => Future[T]): Future[T] = {
        val start = System.nanoTime()

        def run(): Future[T] = {
          metrics.queue(start)
          metrics.run(task)
        }

        sequentially.async(key)(run())
      }
    }

  trait Factory {
    def apply[K](name: String): SequentiallyAsync[K]
  }

  object Factory {

    trait Provider {
      def apply[K]: SequentiallyAsync[K]
    }

    def apply(
      provider: Provider,
      sequentiallyMetrics: SequentiallyMetrics.Factory,
    ): Factory = new Factory {
      override def apply[K](name: String): SequentiallyAsync[K] =
        MeteredSequentiallyAsync(provider.apply[K], sequentiallyMetrics(name))
    }
  }

}

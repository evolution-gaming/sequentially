package com.evolutiongaming.concurrent

import cats.effect.kernel.Sync
import cats.syntax.all.*
import com.evolutiongaming.prometheus.PrometheusHelper.*
import io.prometheus.client.{CollectorRegistry, Summary}

trait SequentiallyMetricsF[F[_]] {
  def queue(startNanos: Long): F[Unit]
  def run[T](task: => F[T]): F[T]
}

object SequentiallyMetricsF {

  type Factory[F[_]] = String => SequentiallyMetricsF[F]

  object Factory {

    /** @note Must be singleton as metric names must be unique.
      * @see CollectorRegistry#register
      */
    def apply[F[_] : Sync](
      prometheusRegistry: CollectorRegistry,
      prefix: String = "sequentially",
    ): Factory[F] = {
      val time = Summary
        .build()
        .name(s"${ prefix }_time")
        .help("Latency of Sequentially operations (queue, run) (by name)")
        .labelNames("name", "operation")
        .defaultQuantiles()
        .register(prometheusRegistry)

      name =>
        new SequentiallyMetricsF[F] {
          def queue(startNanos: Long): F[Unit] = {
            Sync[F].delay {
              time.labels(name, "queue").timeTillNowNanos(startNanos)
            }
          }

          def run[T](task: => F[T]): F[T] = {
            Sync[F].defer {
              val start = System.nanoTime()
              task.flatMap { result =>
                Sync[F].delay {
                  time.labels(name, "run").observe((System.nanoTime() - start).toDouble / 1e9)
                  result
                }
              }
            }
          }
        }
    }
  }
}

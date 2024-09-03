package com.evolutiongaming.concurrent

import com.evolutiongaming.prometheus.PrometheusHelper.*
import io.prometheus.client.{CollectorRegistry, Summary}

import scala.concurrent.Future

trait SequentiallyMetrics {
  def queue(startNanos: Long): Unit
  def run[T](future: => Future[T]): Future[T]
}

object SequentiallyMetrics {

  def empty: SequentiallyMetrics = new SequentiallyMetrics {
    def queue(startNanos: Long): Unit = ()
    def run[T](future: => Future[T]): Future[T] = future
  }

  /** name: String => SequentiallyMetrics */
  type Factory = String => SequentiallyMetrics

  object Factory {

    def empty: Factory = _ => SequentiallyMetrics.empty

    /** @note Must be singleton as metric names must be unique.
      * @see CollectorRegistry#register
      */
    def apply(
      prometheusRegistry: CollectorRegistry,
      prefix: String = "sequentially",
    ): Factory = {
      val time = Summary
        .build()
        .name(s"${ prefix }_time")
        .help("Latency of Sequentially operations (queue, run) (by name)")
        .labelNames("name", "operation")
        .defaultQuantiles()
        .register(prometheusRegistry)

      name =>
        new SequentiallyMetrics {
          def queue(startNanos: Long): Unit = {
            time.labels(name, "queue").timeTillNowNanos(startNanos)
          }

          def run[T](future: => Future[T]): Future[T] = {
            time.labels(name, "run").timeFuture(future)
          }
        }
    }

  }

}

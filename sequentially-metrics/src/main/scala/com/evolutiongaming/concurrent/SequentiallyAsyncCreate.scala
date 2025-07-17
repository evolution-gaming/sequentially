package com.evolutiongaming.concurrent

import akka.stream.Materializer
import com.evolutiongaming.concurrent.sequentially.SequentiallyAsync
import com.evolutiongaming.concurrent.{MeteredSequentiallyAsync, SequentiallyMetrics}

class SequentiallyAsyncCreate(
  materializer: Materializer,
  sequentiallyMetrics: SequentiallyMetrics.Factory,
) {
  def apply[K](name: String): SequentiallyAsync[K] = {
    val sequentially = SequentiallyAsync[K]()(materializer)
    val metrics = sequentiallyMetrics(name)
    MeteredSequentiallyAsync(sequentially, metrics)
  }
}

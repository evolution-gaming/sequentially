package com.evolutiongaming.concurrent.sequentially

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.openjdk.jmh.annotations.{Benchmark, Level, Setup, TearDown}

import scala.concurrent.Await
import scala.concurrent.duration._

class SequentiallyAsyncBenchmark extends Common {

  var system: ActorSystem                  = _
  var sequentially: SequentiallyAsync[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem("benchmark")
    sequentially = {
      val materializer = Materializer(system)
      SequentiallyAsync()(materializer)
    }
  }

  @Benchmark
  def apply(): Unit = {
    Await.result(sequentially(0) {}, 10.seconds)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    Await.ready(system.terminate(), 15.seconds)
    ()
  }
}

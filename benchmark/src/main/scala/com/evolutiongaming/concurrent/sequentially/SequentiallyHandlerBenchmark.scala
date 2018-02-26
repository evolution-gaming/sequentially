package com.evolutiongaming.concurrent.sequentially

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.openjdk.jmh.annotations.{Benchmark, Level, Setup, TearDown}

import scala.concurrent.Await
import scala.concurrent.duration._

class SequentiallyHandlerBenchmark extends Common {

  var system: ActorSystem = _
  var sequentially: SequentiallyHandler[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem("benchmark")
    sequentially = {
      val materializer = ActorMaterializer()(system)
      SequentiallyHandler()(materializer)
    }
  }

  @Benchmark
  def apply(): Unit = {
    Await.result(sequentially(0) {}, 10.seconds)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    Await.ready(system.terminate(), 15.seconds)
  }
}


package com.evolutiongaming.concurrent.sequentially

import akka.actor.ActorSystem
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration._


class SequentiallyBenchmark extends Common {

  var system: ActorSystem = _
  var sequentially: Sequentially[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    system = ActorSystem("benchmark")
    sequentially = Sequentially(system)
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
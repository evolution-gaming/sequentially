package com.evolutiongaming.concurrent.sequentially

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.openjdk.jmh.annotations.*

import scala.concurrent.Await
import scala.concurrent.duration.*

class SequentiallyCatsBenchmark extends Common {

  var sequentially: Sequentially[Int] = _
  var cleanup: IO[Unit] = _
  implicit var runtime: IORuntime = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    runtime = IORuntime.global
    val (seq, clean) = SequentiallyCats.resource[IO, Int].allocated.unsafeRunSync()(runtime)
    sequentially = seq
    cleanup = clean
  }

  @Benchmark
  def apply(): Unit = {
    Await.result(sequentially(0) {}, 10.seconds)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    cleanup.unsafeRunSync()(runtime)
  }
}


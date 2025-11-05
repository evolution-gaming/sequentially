package com.evolutiongaming.concurrent.sequentially

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import org.openjdk.jmh.annotations.*

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.Random

@State(Scope.Benchmark)
class SequentiallyCatsBenchmark extends Common {

  var sequentially: SequentiallyF[IO, Int] = _
  var cleanup: IO[Unit] = _
  var dispatcherCleanup: IO[Unit] = _
  implicit var runtime: IORuntime = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    runtime = IORuntime.global
    
    // Create Dispatcher first
    val (disp, dispClean) = Dispatcher.parallel[IO].allocated.unsafeRunSync()(runtime)
    dispatcherCleanup = dispClean
    
    // Now create SequentiallyF with the dispatcher in implicit scope
    implicit val dispatcher: Dispatcher[IO] = disp
    val (seq, clean) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()(runtime)
    sequentially = seq
    cleanup = clean
  }

  @Benchmark
  def apply(): Unit = {
    Await.result(sequentially(Random.nextInt()) {}, 10.seconds)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    cleanup.unsafeRunSync()(runtime)
    dispatcherCleanup.unsafeRunSync()(runtime)
  }
}

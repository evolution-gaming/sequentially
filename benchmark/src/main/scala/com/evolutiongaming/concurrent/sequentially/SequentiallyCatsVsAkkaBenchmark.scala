package com.evolutiongaming.concurrent.sequentially

import akka.actor.ActorSystem
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import org.openjdk.jmh.annotations.*

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.Random

@State(Scope.Benchmark)
class SequentiallyCatsVsAkkaBenchmark extends Common {

  private var akkaSystem: ActorSystem = _
  private var akkaSequentially: Sequentially[Int] = _
  
  private var catsSequentially: SequentiallyF[IO, Int] = _
  private var catsCleanup: IO[Unit] = _
  var dispatcherCleanup: IO[Unit] = _
  implicit var runtime: IORuntime = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    // Setup Akka
    akkaSystem = ActorSystem("benchmark")
    akkaSequentially = Sequentially(akkaSystem)
    
    // Setup Cats Effect
    runtime = IORuntime.global
    
    // Create Dispatcher first
    val (disp, dispClean) = Dispatcher.parallel[IO].allocated.unsafeRunSync()(runtime)
    dispatcherCleanup = dispClean
    
    // Now create SequentiallyF with the dispatcher in implicit scope
    implicit val dispatcher: Dispatcher[IO] = disp
    val (seq, clean) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()(runtime)
    catsSequentially = seq
    catsCleanup = clean
  }

  @Benchmark
  def akkaFuture(): Unit = {
    // Akka returns Future
    Await.result(akkaSequentially(Random.nextInt()) {}, 10.seconds)
  }

  @Benchmark
  def catsApplyF(): Unit = {
    // applyF returns F[T], run it directly
    catsSequentially.applyF(Random.nextInt())(IO.unit).unsafeRunSync()(runtime)
  }

  @Benchmark
  def catsApplyFuture(): Unit = {
    // For fairness, also benchmark the Future-based apply
    Await.result(catsSequentially(Random.nextInt()) {}, 10.seconds)
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    catsCleanup.unsafeRunSync()(runtime)
    dispatcherCleanup.unsafeRunSync()(runtime)
    Await.ready(akkaSystem.terminate(), 15.seconds)
    ()
  }
}


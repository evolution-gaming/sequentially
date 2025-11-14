package com.evolutiongaming.concurrent.sequentially

import akka.actor.ActorSystem
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.syntax.all.*
import org.openjdk.jmh.annotations.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.util.Random

@State(Scope.Benchmark)
class SequentiallyCatsVsAkkaBenchmark extends Common {

  private val BatchSize = 1000

  private var akkaSystem: ActorSystem = _
  private var akkaSequentially: Sequentially[Int] = _

  private var catsSequentially: SequentiallyF[IO, Int] = _
  private var catsCleanup: IO[Unit] = _
  private var dispatcherCleanup: IO[Unit] = _
  private var dispatcher: Dispatcher[IO] = _

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
    dispatcher = disp
    dispatcherCleanup = dispClean

    val (seq, clean) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()(runtime)
    catsSequentially = seq
    catsCleanup = clean
  }

  @Benchmark
  def akkaFuture(): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = akkaSystem.dispatcher
    val futures = List.fill(BatchSize)(akkaSequentially(Random.nextInt()) {})
    Await.result(Future.sequence(futures), 10.seconds)
    ()
  }

  @Benchmark
  def catsApplyF(): Unit = {
    val operations = List.fill(BatchSize)(
      catsSequentially.applyF(Random.nextInt())(IO.unit)
    )
    operations.sequence.unsafeRunSync()(runtime)
    ()
  }

  @Benchmark
  def catsApplyFuture(): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val futures = List.fill(BatchSize)(catsSequentially(Random.nextInt()) {}(dispatcher))
    Await.result(Future.sequence(futures), 10.seconds)
    ()
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    catsCleanup.unsafeRunSync()(runtime)
    dispatcherCleanup.unsafeRunSync()(runtime)
    Await.ready(akkaSystem.terminate(), 15.seconds)
    ()
  }
}

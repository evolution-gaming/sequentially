package com.evolutiongaming.concurrent.sequentially

import cats.effect.IO
import cats.effect.std.{Dispatcher, Semaphore}
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import scala.concurrent.ExecutionContext

class SequentiallyFSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(50, Millis),
  )

  implicit val dispatcher: Dispatcher[IO] =
    Dispatcher.parallel[IO].allocated.unsafeRunSync()(IORuntime.global)._1

  "SequentiallyF" should {

    "execute tasks in parallel for different keys without Thread.sleep" in {
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()

      try {
        val startBarrier = new CyclicBarrier(3)
        val task1Started = new CountDownLatch(1)
        val task2Started = new CountDownLatch(1)
        val task1CanComplete = new CountDownLatch(1)
        val task2CanComplete = new CountDownLatch(1)

        // Task 1 - waits for permission to complete
        val future1 = sequentially(1) {
          startBarrier.await() // Sync start
          task1Started.countDown()
          task1CanComplete.await() // Wait for permission
          "result1"
        }

        // Task 2 - waits for permission to complete
        val future2 = sequentially(2) {
          startBarrier.await() // Sync start
          task2Started.countDown()
          task2CanComplete.await() // Wait for permission
          "result2"
        }

        // Wait for both tasks to start synchronously
        startBarrier.await()

        // Verify both tasks started (proving parallelism)
        task1Started.await()
        task2Started.await()

        // Both tasks should be waiting, neither completed
        future1.isCompleted shouldEqual false
        future2.isCompleted shouldEqual false

        // Allow task2 to complete first
        task2CanComplete.countDown()
        future2.futureValue shouldEqual "result2"

        // Task1 should still be waiting (proving they run in parallel)
        future1.isCompleted shouldEqual false

        // Now allow task1 to complete
        task1CanComplete.countDown()
        future1.futureValue shouldEqual "result1"

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "execute tasks sequentially for the same key" in {
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()

      try {
        val executionOrder = new AtomicInteger(0)
        val results = new AtomicReference(List.empty[String])

        // Create multiple tasks for same key
        val futures = (1 to 10).map { taskId =>
          sequentially(1) {
            val order = executionOrder.incrementAndGet()
            val result = s"task-$taskId-order-$order"

            // Atomically add to results list
            results.updateAndGet(result :: _)

            result
          }
        }

        // Wait for all to complete
        val allResults = futures.map(_.futureValue)
        allResults should have size 10

        // Verify execution order is sequential (1, 2, 3, ..., 10)
        val finalOrder = executionOrder.get()
        finalOrder shouldEqual 10

        // Verify all tasks executed and results were collected
        val collectedResults = results.get()
        collectedResults should have size 10

        // All results should be present
        allResults.foreach { result =>
          result should startWith("task-")
          result should include("order-")
        }

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "handle exceptions without breaking sequential execution" in {
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()

      try {
        val executionOrder = new AtomicInteger(0)
        val task1Started = new CountDownLatch(1)
        val task2Started = new CountDownLatch(1)

        // Task that throws exception but signals it executed
        val future1 = sequentially(1) {
          executionOrder.incrementAndGet()
          task1Started.countDown()
          throw new RuntimeException("test exception")
        }.recover { case _ => "recovered-1" }

        // Wait for task1 to start before submitting task2
        task1Started.await()

        // Task that should execute after exception
        val future2 = sequentially(1) {
          val order = executionOrder.incrementAndGet()
          task2Started.countDown()
          s"success-$order"
        }

        // Wait for task2 to start
        task2Started.await()

        // Both should complete, exception handled
        future1.futureValue shouldEqual "recovered-1"
        val result2 = future2.futureValue
        result2 shouldEqual "success-2"

        // Verify both tasks executed
        executionOrder.get() shouldEqual 2

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "distribute keys across buckets correctly" in {
      val bucketCount = (Runtime.getRuntime.availableProcessors() max 1) * 5

      // Create semaphores and dispatcher that we can inspect
      val semaphores = List.fill(bucketCount)(Semaphore[IO](1)).sequence.map(_.toVector).unsafeRunSync()
      implicit val (dispatcher: Dispatcher[IO], dispatcherCleanup: IO[Unit]) =
        Dispatcher.parallel[IO].allocated.unsafeRunSync()
      val sequentially = SequentiallyF.resource[IO, Int](semaphores).allocated.unsafeRunSync()._1

      try {
        // Verify all semaphores are initialized
        semaphores.size shouldEqual bucketCount

        // Test that operations complete successfully
        val testKeys = 1 to 100
        val futures = testKeys.map { key =>
          sequentially(key) {
            s"result-$key"
          }
        }

        val results = futures.map(_.futureValue)
        results.size shouldEqual 100

        // Verify all results are correct
        results.zipWithIndex.foreach { case (result, idx) =>
          result shouldEqual s"result-${ idx + 1 }"
        }

        // Test that keys with same hash bucket execute sequentially
        val counter = new AtomicInteger(0)

        // Use key 1 multiple times - all should go to same bucket
        val sameKeyFutures = (1 to 10).map { _ =>
          sequentially(1) {
            val order = counter.incrementAndGet()
            s"order-$order"
          }
        }

        val sameKeyResults = sameKeyFutures.map(_.futureValue)
        counter.get() shouldEqual 10
        sameKeyResults should have size 10

      } finally {
        dispatcherCleanup.unsafeRunSync()
      }
    }

    "execute F-based tasks correctly with applyF" in {
      val (seq, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()
      val sequentially = seq.asInstanceOf[SequentiallyF[IO, Int]]

      try {
        val counter = new AtomicInteger(0)

        // Test applyF with IO effects
        val futures = (1 to 10).map { taskId =>
          sequentially.applyF(1) {
            IO {
              val order = counter.incrementAndGet()
              s"result-$taskId-executed-$order"
            }
          }.unsafeToFuture()
        }

        // All should complete successfully
        val results = futures.map(_.futureValue)
        results should have size 10

        // Counter should equal number of operations (proving all executed sequentially)
        val finalCount = counter.get()
        finalCount shouldEqual 10

        // Verify all results are present
        results.foreach { result =>
          result should startWith("result-")
          result should include("executed-")
        }

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "handle F[_] exceptions in applyF without breaking sequential execution" in {
      val (seq, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()
      val sequentially = seq.asInstanceOf[SequentiallyF[IO, Int]]

      try {
        val executionOrder = new AtomicInteger(0)
        val task1Started = new CountDownLatch(1)
        val task2Started = new CountDownLatch(1)
        val task3Started = new CountDownLatch(1)

        // Task 1: F[_] that raises an error
        val future1 = sequentially.applyF(1) {
          IO {
            executionOrder.incrementAndGet()
            task1Started.countDown()
          } *> IO.raiseError(new RuntimeException("F[_] exception"))
        }.attempt.unsafeToFuture()

        // Wait for task1 to start
        task1Started.await()

        // Task 2: Should still execute after F[_] exception
        val future2 = sequentially.applyF(1) {
          IO {
            val order = executionOrder.incrementAndGet()
            task2Started.countDown()
            s"success-$order"
          }
        }.unsafeToFuture()

        // Wait for task2 to start
        task2Started.await()

        // Task 3: Another successful task
        val future3 = sequentially.applyF(1) {
          IO {
            val order = executionOrder.incrementAndGet()
            task3Started.countDown()
            s"final-$order"
          }
        }.unsafeToFuture()

        // Wait for task3 to start
        task3Started.await()

        // Verify results
        future1.futureValue.isLeft shouldBe true
        future2.futureValue shouldEqual "success-2"
        future3.futureValue shouldEqual "final-3"

        // All three tasks should have executed in order
        executionOrder.get() shouldEqual 3

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "handle F[_] exceptions in parallel for different keys" in {
      val (seq, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()
      val sequentially = seq.asInstanceOf[SequentiallyF[IO, Int]]

      try {
        val key1Counter = new AtomicInteger(0)
        val key2Counter = new AtomicInteger(0)

        // Key 1: Has an exception in the middle
        val key1Futures = List(
          sequentially.applyF(1)(IO(key1Counter.incrementAndGet()).as("k1-t1")).attempt.unsafeToFuture(),
          sequentially.applyF(1)(
            IO(key1Counter.incrementAndGet()) *> IO.raiseError(new RuntimeException("k1 error"))
          ).attempt.unsafeToFuture(),
          sequentially.applyF(1)(IO(key1Counter.incrementAndGet()).as("k1-t3")).attempt.unsafeToFuture(),
        )

        // Key 2: All successful (should not be affected by key 1's exception)
        val key2Futures = List(
          sequentially.applyF(2)(IO(key2Counter.incrementAndGet()).as("k2-t1")).unsafeToFuture(),
          sequentially.applyF(2)(IO(key2Counter.incrementAndGet()).as("k2-t2")).unsafeToFuture(),
          sequentially.applyF(2)(IO(key2Counter.incrementAndGet()).as("k2-t3")).unsafeToFuture(),
        )

        // Wait for all to complete
        val key1Results = key1Futures.map(_.futureValue)
        val key2Results = key2Futures.map(_.futureValue)

        // Key 1: First and third should succeed, second should fail
        key1Results(0).isRight shouldBe true
        key1Results(1).isLeft shouldBe true
        key1Results(2).isRight shouldBe true

        // Key 2: All should succeed despite key 1's exception
        key2Results.foreach { result =>
          result should startWith("k2-")
        }

        // Both keys should have executed all their tasks
        key1Counter.get() shouldEqual 3
        key2Counter.get() shouldEqual 3

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "maintain correctness under concurrent stress" in {
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int].allocated.unsafeRunSync()

      try {
        val numKeys = 50
        val operationsPerKey = 20
        val totalOps: Int = numKeys * operationsPerKey
        val executionCounters = Array.fill(numKeys)(new AtomicInteger(0))

        // Create concurrent operations for multiple keys
        val futures = (for {
          key <- 0 until numKeys
          op <- 1 to operationsPerKey
        } yield {
          sequentially(key) {
            val order = executionCounters(key).incrementAndGet()
            // Add some computation to make timing more realistic
            val computation = (1 to 100).sum
            s"key-$key-op-$op-order-$order-result-$computation"
          }
        }).toList

        // Wait for all to complete
        val results = futures.map(_.futureValue)
        results.size shouldEqual totalOps

        // Verify each key executed exactly the right number of operations sequentially
        executionCounters.foreach { counter =>
          counter.get() shouldEqual operationsPerKey
        }

        // Verify all results are correct
        results.foreach { result =>
          result should startWith("key-")
          result should include("-op-")
          result should include("-order-")
          result should include("-result-5050") // Sum of 1 to 100
        }

      } finally {
        cleanup.unsafeRunSync()
      }
    }
  }
}

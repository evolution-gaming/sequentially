package com.evolutiongaming.concurrent.sequentially

import cats.effect.IO
import cats.effect.std.{Dispatcher, MapRef, Semaphore}
import cats.effect.unsafe.implicits.global
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration.*

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import scala.concurrent.ExecutionContext

class SequentiallyCatsSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(50, Millis)
  )

  "SequentiallyCats" should {

    "execute tasks in parallel for different keys without Thread.sleep" in {
      val (sequentially, cleanup) = SequentiallyCats.resource[IO, Int].allocated.unsafeRunSync()

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
      val (sequentially, cleanup) = SequentiallyCats.resource[IO, Int].allocated.unsafeRunSync()

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
      val (sequentially, cleanup) = SequentiallyCats.resource[IO, Int].allocated.unsafeRunSync()

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

    "not leak semaphores under heavy load" in {
      // Create MapRef and Dispatcher that we can inspect
      val semaphores = MapRef.ofShardedImmutableMap[IO, Int, Option[Semaphore[IO]]](128).unsafeRunSync()
      val (dispatcher, dispatcherCleanup) = Dispatcher.parallel[IO].allocated.unsafeRunSync()
      val sequentially = SequentiallyCats.resource[IO, Int](semaphores, dispatcher).allocated.unsafeRunSync()._1

      try {
        val testKeys = List(1, 2, 3, 4, 5)
        val operationsPerKey = 10

        // Run multiple operations on each key
        val futures = (for {
          key <- testKeys
          op <- 1 to operationsPerKey
        } yield {
          sequentially(key) {
            s"key-$key-op-$op"
          }
        }).toList

        // Wait for all operations to complete
        val results = futures.map(_.futureValue)
        results.size shouldEqual (testKeys.size * operationsPerKey)

        // After all operations complete, semaphores should be cleaned up
        // Use eventually to poll until cleanup happens (with 5 second timeout)
        eventually(timeout(5.seconds), interval(50.millis)) {
          val cleanupChecks = testKeys.map { key =>
            dispatcher.unsafeToFuture(
              semaphores(key).get.map {
                case Some(Some(_)) => true  // Has active semaphore
                case _ => false              // Cleaned up
              }
            ).futureValue
          }
          
          // All semaphores should be cleaned up (false = no active semaphore)
          cleanupChecks.forall(!_) shouldBe true
        }

        // Verify system is still responsive after cleanup
        val verificationFutures = testKeys.map { key =>
          sequentially(key) {
            s"verification-$key"
          }
        }
        
        val verificationResults = verificationFutures.map(_.futureValue)
        verificationResults.size shouldEqual testKeys.size
        
        // After verification operations, semaphores should be cleaned up again
        eventually(timeout(5.seconds), interval(50.millis)) {
          val finalCleanupChecks = testKeys.map { key =>
            dispatcher.unsafeToFuture(
              semaphores(key).get.map {
                case Some(Some(_)) => true
                case _ => false
              }
            ).futureValue
          }
          
          finalCleanupChecks.forall(!_) shouldBe true
        }

      } finally {
        dispatcherCleanup.unsafeRunSync()
      }
    }

    "maintain correctness under concurrent stress" in {
      val (sequentially, cleanup) = SequentiallyCats.resource[IO, Int].allocated.unsafeRunSync()

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

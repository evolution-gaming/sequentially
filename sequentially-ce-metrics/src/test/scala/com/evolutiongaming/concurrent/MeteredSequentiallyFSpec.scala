package com.evolutiongaming.concurrent

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.evolutiongaming.concurrent.sequentially.SequentiallyF
import io.prometheus.client.CollectorRegistry
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext

class MeteredSequentiallyFSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(50, Millis),
  )

  "MeteredSequentiallyF" should {

    "execute tasks sequentially for the same key using applyF" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-sequential", metricsFactory)

      try {
        val executionOrder = new AtomicInteger(0)
        val results = new AtomicInteger(0)

        val tasks = (1 to 10).map { i =>
          metered.applyF(1) {
            IO {
              val order = executionOrder.incrementAndGet()
              results.addAndGet(i)
              s"task-$i-order-$order"
            }
          }
        }

        val allResults = tasks.toList.sequence.unsafeRunSync()
        allResults should have size 10

        executionOrder.get() shouldEqual 10
        results.get() shouldEqual 55 // Sum of 1 to 10

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "execute tasks in parallel for different keys using applyF" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-parallel", metricsFactory)

      try {
        val key1Counter = new AtomicInteger(0)
        val key2Counter = new AtomicInteger(0)

        val task1 = metered.applyF(1)(IO(key1Counter.incrementAndGet()).as("key1-result"))
        val task2 = metered.applyF(2)(IO(key2Counter.incrementAndGet()).as("key2-result"))

        val results = (task1, task2).tupled.unsafeRunSync()

        val (key1, key2) = results
        key1 shouldEqual "key1-result"
        key2 shouldEqual "key2-result"

        key1Counter.get() shouldEqual 1
        key2Counter.get() shouldEqual 1

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "record metrics for applyF operations" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-metrics", metricsFactory)

      try {
        metered.applyF(1)(IO.pure("result")).unsafeRunSync()

        val queueCount = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("test-metrics", "queue")
        )
        val runCount = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("test-metrics", "run")
        )

        queueCount.doubleValue() should be >= 0.0
        runCount.doubleValue() should be >= 0.0

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "execute tasks sequentially for the same key using apply" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val (dispatcher, dispatcherCleanup) = Dispatcher.parallel[IO].allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-apply", metricsFactory)
      
      implicit val disp: Dispatcher[IO] = dispatcher

      try {
        val executionOrder = new AtomicInteger(0)

        val futures = (1 to 10).map { i =>
          metered(1) {
            val order = executionOrder.incrementAndGet()
            s"task-$i-order-$order"
          }
        }

        val allResults = futures.map(_.futureValue)
        allResults should have size 10
        executionOrder.get() shouldEqual 10

      } finally {
        dispatcherCleanup.unsafeRunSync()
        cleanup.unsafeRunSync()
      }
    }

    "record metrics for apply operations" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val (dispatcher, dispatcherCleanup) = Dispatcher.parallel[IO].allocated.unsafeRunSync()
      implicit val disp: Dispatcher[IO] = dispatcher
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-apply-metrics", metricsFactory)

      try {
        metered(1)("result").futureValue

        val queueCount = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("test-apply-metrics", "queue")
        )
        val runCount = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("test-apply-metrics", "run")
        )

        queueCount.doubleValue() should be >= 0.0
        runCount.doubleValue() should be >= 0.0

      } finally {
        dispatcherCleanup.unsafeRunSync()
        cleanup.unsafeRunSync()
      }
    }

    "handle exceptions in applyF" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-error", metricsFactory)

      try {
        val task = metered.applyF(1)(IO.raiseError[String](new RuntimeException("test error")))

        assertThrows[RuntimeException] {
          task.unsafeRunSync()
        }

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "handle exceptions in apply" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val (dispatcher, dispatcherCleanup) = Dispatcher.parallel[IO].allocated.unsafeRunSync()
      implicit val disp: Dispatcher[IO] = dispatcher
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-error-apply", metricsFactory)

      try {
        val future = metered(1) {
          throw new RuntimeException("test error")
        }

        assertThrows[RuntimeException] {
          future.futureValue
        }

      } finally {
        dispatcherCleanup.unsafeRunSync()
        cleanup.unsafeRunSync()
      }
    }

    "record metrics for multiple operations" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-multiple", metricsFactory)

      try {
        (1 to 10).foreach { i =>
          metered.applyF(1)(IO.pure(i)).unsafeRunSync()
        }

        val queueCount = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("test-multiple", "queue")
        )
        val runCount = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("test-multiple", "run")
        )

        queueCount.doubleValue() shouldEqual 10.0
        runCount.doubleValue() shouldEqual 10.0

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "use different metric names for different instances" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered1 = MeteredSequentiallyF(sequentially, "name1", metricsFactory)
      val metered2 = MeteredSequentiallyF(sequentially, "name2", metricsFactory)

      try {
        metered1.applyF(1)(IO.pure("result1")).unsafeRunSync()
        metered2.applyF(2)(IO.pure("result2")).unsafeRunSync()

        val count1 = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("name1", "run")
        )
        val count2 = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("name2", "run")
        )

        count1.doubleValue() should be >= 0.0
        count2.doubleValue() should be >= 0.0

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "maintain sequential execution after exception" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      val metered = MeteredSequentiallyF(sequentially, "test-recovery", metricsFactory)

      try {
        val executionOrder = new AtomicInteger(0)

        val task1 = metered.applyF(1) {
          IO {
            executionOrder.incrementAndGet()
          } *> IO.raiseError[String](new RuntimeException("error"))
        }.attempt

        val task2 = metered.applyF(1) {
          IO {
            val order = executionOrder.incrementAndGet()
            s"success-$order"
          }
        }

        task1.unsafeRunSync().isLeft shouldBe true
        val result2 = task2.unsafeRunSync()
        result2 shouldEqual "success-2"
        executionOrder.get() shouldEqual 2

      } finally {
        cleanup.unsafeRunSync()
      }
    }
  }

  "MeteredSequentiallyF.Factory" should {

    "create instances correctly" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      
      val provider = new MeteredSequentiallyF.Factory.Provider[IO] {
        override def apply[K]: SequentiallyF[IO, K] = sequentially.asInstanceOf[SequentiallyF[IO, K]]
      }
      
      val factory = MeteredSequentiallyF.Factory(provider, metricsFactory)

      try {
        val metered = factory("test-factory")
        
        val result = metered.applyF(1)(IO.pure("result")).unsafeRunSync()
        result shouldEqual "result"

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "create different instances for different names" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      
      val provider = new MeteredSequentiallyF.Factory.Provider[IO] {
        override def apply[K]: SequentiallyF[IO, K] = sequentially.asInstanceOf[SequentiallyF[IO, K]]
      }
      
      val factory = MeteredSequentiallyF.Factory(provider, metricsFactory)

      try {
        val metered1 = factory("name1")
        val metered2 = factory("name2")

        metered1 should not be metered2

        metered1.applyF(1)(IO.pure("result1")).unsafeRunSync() shouldEqual "result1"
        metered2.applyF(2)(IO.pure("result2")).unsafeRunSync() shouldEqual "result2"

      } finally {
        cleanup.unsafeRunSync()
      }
    }

    "record metrics with correct names" in {
      val registry = new CollectorRegistry()
      val (sequentially, cleanup) = SequentiallyF.resource[IO, Int]().allocated.unsafeRunSync()
      val metricsFactory = SequentiallyMetricsF.Factory[IO](registry)
      
      val provider = new MeteredSequentiallyF.Factory.Provider[IO] {
        override def apply[K]: SequentiallyF[IO, K] = sequentially.asInstanceOf[SequentiallyF[IO, K]]
      }
      
      val factory = MeteredSequentiallyF.Factory(provider, metricsFactory)

      try {
        val metered = factory("factory-test")
        metered.applyF(1)(IO.pure("result")).unsafeRunSync()

        val count = registry.getSampleValue(
          "sequentially_time_count",
          Array("name", "operation"),
          Array("factory-test", "run")
        )

        count.doubleValue() should be >= 0.0

      } finally {
        cleanup.unsafeRunSync()
      }
    }
  }
}


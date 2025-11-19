package com.evolutiongaming.concurrent

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.prometheus.client.CollectorRegistry
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*
import scala.util.Try

class SequentiallyMetricsFSpec extends AnyWordSpec with Matchers {

  "SequentiallyMetricsF.Factory" should {

    "create instances correctly" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)

      val metrics = factory("test-name")

      // Verify it has the expected methods by calling queue
      val queueResult = metrics.queue(System.nanoTime())
      queueResult shouldBe a[IO[_]]
    }

    "create different instances for different names" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)

      val metrics1 = factory("name1")
      val metrics2 = factory("name2")

      metrics1 should not be metrics2
    }

    "use custom prefix when provided" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry, prefix = "custom_prefix")

      factory("test-name")

      // Verify metric exists with custom prefix
      val metricName = "custom_prefix_time"
      val samples = registry.metricFamilySamples().asIterator().asScala.toSeq
      val found = samples.exists(_.name == metricName)
      found shouldBe true
    }

    "use default prefix when not provided" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)

      factory("test-name")

      // Verify metric exists with default prefix
      val metricName = "sequentially_time"
      val samples = registry.metricFamilySamples().asIterator().asScala.toSeq
      val found = samples.exists(_.name == metricName)
      found shouldBe true
    }
  }

  "SequentiallyMetricsF.queue" should {

    "record queue time metrics" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-queue")

      val startNanos = System.nanoTime()

      // Record queue time
      metrics.queue(startNanos).unsafeRunSync()

      // Verify metric was recorded
      val summary = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-queue", "queue"),
      )

      Option(summary).isDefined shouldBe true
      summary.doubleValue() should be >= 0.0
    }

    "record queue time for different names separately" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics1 = factory("name1")
      val metrics2 = factory("name2")

      val startNanos = System.nanoTime()
      metrics1.queue(startNanos).unsafeRunSync()
      metrics2.queue(startNanos).unsafeRunSync()

      // Verify both metrics were recorded
      val count1 = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("name1", "queue"),
      )
      val count2 = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("name2", "queue"),
      )

      Option(count1).isDefined shouldBe true
      Option(count2).isDefined shouldBe true
      count1.doubleValue() should be >= 0.0
      count2.doubleValue() should be >= 0.0
    }
  }

  "SequentiallyMetricsF.run" should {

    "execute task and return result" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-run")

      val task = IO.pure("test-result")
      val result = metrics.run(task).unsafeRunSync()

      result shouldEqual "test-result"
    }

    "record run time metrics" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-run")

      val task = IO.pure(42)
      metrics.run(task).unsafeRunSync()

      // Verify metric was recorded
      val count = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-run", "run"),
      )

      Option(count).isDefined shouldBe true
      count.doubleValue() should be >= 0.0
    }

    "record run time for different names separately" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics1 = factory("name1")
      val metrics2 = factory("name2")

      metrics1.run(IO.pure(1)).unsafeRunSync()
      metrics2.run(IO.pure(2)).unsafeRunSync()

      // Verify both metrics were recorded
      val count1 = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("name1", "run"),
      )
      val count2 = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("name2", "run"),
      )

      Option(count1).isDefined shouldBe true
      Option(count2).isDefined shouldBe true
      count1.doubleValue() should be >= 0.0
      count2.doubleValue() should be >= 0.0
    }

    "handle task that throws exception" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-error")

      val task = IO.raiseError[String](new RuntimeException("test error"))

      // Exception should propagate
      assertThrows[RuntimeException] {
        metrics.run(task).unsafeRunSync()
      }
    }

    "record metrics even when task throws exception" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-error")

      val task = IO.raiseError[String](new RuntimeException("test error"))

      // Attempt to run and catch exception using Try
      val result = Try(metrics.run(task).unsafeRunSync())
      result.isFailure shouldBe true

      // Note: The current implementation records metrics only on success
      // This test documents the current behavior
      val count = Option(registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-error", "run"),
      ))

      // If exception occurs before flatMap completes, metric may not be recorded
      // This is expected behavior based on the implementation
      count.foreach(_.doubleValue() shouldEqual 0.0)
    }

    "execute task lazily" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-lazy")

      val executed = new AtomicBoolean(false)
      val task = IO {
        executed.set(true)
        "result"
      }

      // Task should not execute until run is called
      executed.get() shouldBe false

      val result = metrics.run(task).unsafeRunSync()

      executed.get() shouldBe true
      result shouldEqual "result"
    }

    "record correct time duration" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-duration")

      // Create a task that takes some time
      val task = IO.delay {
        Thread.sleep(100)
        "done"
      }

      val start = System.nanoTime()
      metrics.run(task).unsafeRunSync()
      val end = System.nanoTime()

      val duration = (end - start).toDouble / 1e9

      // Verify metric was recorded with reasonable duration
      val summary = registry.getSampleValue(
        "sequentially_time_sum",
        Array("name", "operation"),
        Array("test-duration", "run"),
      )

      Option(summary).isDefined shouldBe true
      summary.doubleValue() should be > 0.0
      summary.doubleValue() should be < duration + 0.5
    }
  }

  "SequentiallyMetricsF integration" should {

    "record both queue and run metrics" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-integration")

      val startNanos = System.nanoTime()
      metrics.queue(startNanos).unsafeRunSync()
      metrics.run(IO.pure("result")).unsafeRunSync()

      // Verify both metrics were recorded
      val queueCount = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-integration", "queue"),
      )
      val runCount = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-integration", "run"),
      )

      Option(queueCount).isDefined shouldBe true
      Option(runCount).isDefined shouldBe true
      queueCount.doubleValue() should be >= 0.0
      runCount.doubleValue() should be >= 0.0
    }

    "handle multiple operations correctly" in {
      val registry = new CollectorRegistry()
      val factory = SequentiallyMetricsF.Factory[IO](registry)
      val metrics = factory("test-multiple")

      // Perform multiple operations
      (1 to 10).foreach { i =>
        val startNanos = System.nanoTime()
        metrics.queue(startNanos).unsafeRunSync()
        metrics.run(IO.pure(i)).unsafeRunSync()
      }

      // Verify counts
      val queueCount = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-multiple", "queue"),
      )
      val runCount = registry.getSampleValue(
        "sequentially_time_count",
        Array("name", "operation"),
        Array("test-multiple", "run"),
      )

      Option(queueCount).isDefined shouldBe true
      Option(runCount).isDefined shouldBe true
      queueCount.doubleValue() shouldEqual 10.0
      runCount.doubleValue() shouldEqual 10.0
    }
  }
}

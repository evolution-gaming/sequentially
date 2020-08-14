package com.evolutiongaming.concurrent.sequentially

import akka.stream.{Materializer, OverflowStrategy}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.util.control.NoStackTrace
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SequentiallyAsyncSpec extends AnyWordSpec with ActorSpec with Matchers {

  "SequentiallyAsync" should {

    "run tasks for the same key sequentially" in new Scope() {
      val result1 = sequentially.async(1)(promise1.future)
      val result2 = sequentially.async(1)(promise2.future)

      promise2.success(())
      expectTimeout(result1)
      expectTimeout(result2)

      promise1.success(())
      await(result1)
      await(result2)
    }

    "run tasks for different keys in parallel" in new Scope() {
      val result1 = sequentially.async(1)(promise1.future)
      val result2 = sequentially.async(2)(promise2.future)

      promise2.success(())
      expectTimeout(result1)
      await(result2)

      promise1.success(())
      await(result1)
    }

    "return exceptions" in new Scope() {
      val result1 = sequentially.async(1)(promise1.future)
      promise1.failure(TestException)

      the[TestException.type] thrownBy {
        await(result1)
      }

      val result2 = sequentially.async(1)(promise2.future)
      promise2.success(())
      await(result2)

      val result3 = sequentially.async(1) { throw TestException }
      the[TestException.type] thrownBy {
        await(result3)
      }
    }

    "drop new" in new Scope(1, OverflowStrategy.dropNew) {
      sequentially.async(1)(promise1.future)
      sequentially.async(1)(promise1.future)
      val result1 = sequentially.async(1)(promise1.future)

      val result2 = sequentially.async(1)(promise1.future)
      the[QueueException] thrownBy {
        await(result2)
      }

      promise1.success(())
      await(result1)

      val result3 = sequentially.async(1)(Future.unit)
      await(result3)
    }

    "fail queue" in new Scope(1, OverflowStrategy.fail) {
      sequentially.async(1)(promise1.future)
      sequentially.async(1)(promise1.future)
      sequentially.async(1)(promise1.future)

      val result = sequentially.async(1)(promise1.future)
      the[QueueException] thrownBy {
        await(result)
      }
    }
  }

  private class Scope(bufferSize: Int = Int.MaxValue, overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure) {

    implicit val materializer = Materializer(system)

    val sequentially = SequentiallyAsync[Int](bufferSize = bufferSize, overflowStrategy = overflowStrategy)

    val promise1 = Promise[Unit]()
    val promise2 = Promise[Unit]()

    def expectTimeout[T](future: Future[T]) = {
      the[TimeoutException] thrownBy {
        Await.result(future, 100.millis)
      }
    }

    def await[T](future: Future[T]) = {
      Await.result(future, 300.millis)
    }

    case object TestException extends RuntimeException with NoStackTrace
  }
}

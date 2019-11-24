package com.evolutiongaming.concurrent.sequentially

import akka.stream.Materializer
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.concurrent.FutureHelper._
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}

class AsyncHandlerMapSpec extends WordSpec with Matchers with ActorSpec {

  implicit val ec = CurrentThreadExecutionContext

  type K = Int
  type V = Int

  "AsyncHandlerMap" should {

    "return values" in new Scope {
      map.values shouldEqual Map()
      map.put(0, 0)
      map.values shouldEqual Map(0 -> 0)
    }

    "update when state before is None" in new Scope {
      val result = map.update(0) { before =>
        (MapDirective.update(0), before)
      }
      result.value shouldEqual Some(Success(None))
      map.getNow(0) shouldEqual Some(0)
    }

    "update when state before is Some" in new Scope {
      map.put(0, 0)
      val result = map.update(0) { before =>
        (MapDirective.remove, before)
      }
      result.value shouldEqual Some(Success(Some(0)))
      map.getNow(0) shouldEqual None
    }

    "ignore" in new Scope {
      val result = map.update(0) { before =>
        (MapDirective.ignore, before)
      }
      result.value shouldEqual Some(Success(None))
      map.getNow(0) shouldEqual None
    }

    "remove" in new Scope {
      map.remove(0).value shouldEqual Some(Success(None))
      map.put(0, 0)
      map.remove(0).value shouldEqual Some(Success(Some(0)))
      map.getNow(0) shouldEqual None
    }

    "update async" in new Scope {
      map.updateAsync(0) { before =>
        val directive = MapDirective.update(0)
        val result = (directive, before)
        result.future
      }.value shouldEqual Some(Success(None))

      map.getNow(0) shouldEqual Some(0)

      val promise = Promise[MapDirective[K]]
      val result = map.updateAsync(0) { before =>
        promise.future map { directive =>
          (directive, before)
        }
      }

      result.value shouldEqual None
      map.getNow(0) shouldEqual Some(0)
      promise.success(MapDirective.update(1))
      result.value shouldEqual Some(Success(Some(0)))
      map.getNow(0) shouldEqual Some(1)
    }

    "add value" in new Scope {
      val result = map.update(key) { value =>
        val newValue = (value getOrElse 0) + 1
        val directive = MapDirective.update(newValue)
        (directive, newValue)
      }

      val expected = 1
      result.get shouldEqual Some(expected)
      map.values shouldEqual Map(key -> expected)
    }

    "remove value" in new Scope {
      val result = map.update(key) { value =>
        val newValue = (value getOrElse 0) + 1
        val directive = MapDirective.update(newValue)
        (directive, newValue)
      }

      val expected = 1
      result.get shouldEqual Some(expected)
      map.values.get(key) shouldEqual Some(expected)
      map.remove(key).get.flatten shouldEqual Some(expected)
    }

    "update value" in new Scope {
      val result = map.update(key) { value =>
        val newValue = (value getOrElse 0) + 1
        val directive = MapDirective.update(newValue)
        (directive, newValue)
      }

      val expected = 1
      result.get shouldEqual Some(expected)
      map.values shouldEqual Map(key -> expected)
    }

    "preserve order" in new Scope {
      val p0 = Promise[Unit]
      val p1 = Promise[Unit]
      val result0 = update(1, p0, p1)
      result0.value shouldEqual None

      val p2 = Promise[Unit]
      val p3 = Promise[Unit]
      val result1 = update(2, p2, p3)
      result1.value shouldEqual None

      p0.success(())
      result0.value shouldEqual None
      result1.value shouldEqual None

      p2.success(())
      result0.value shouldEqual None
      result1.value shouldEqual None

      p3.success(())
      result0.value shouldEqual None
      result1.value shouldEqual None

      p1.success(())
      result0.await shouldEqual 1
      result1.await shouldEqual 2

      map.values.get(key) shouldEqual Some(2)

      override def sequentially = SequentiallyHandler()(materializer)
    }

    "handle exceptions" in new Scope {
      map.update(key) { _ => throw TestException }.value shouldEqual Some(Failure(TestException))
      map.updateAsync(key) { _ => Future.failed(TestException) }.value shouldEqual Some(Failure(TestException))
      map.updateHandler(key) { _ => throw TestException }.value shouldEqual Some(Failure(TestException))
      map.updateHandler(key) { _ => Future.failed(TestException) }.value shouldEqual Some(Failure(TestException))
    }
  }

  private implicit val materializer = Materializer(system)

  private trait Scope {
    val key: K = 0
    val map = AsyncHandlerMap[K, V](sequentially)

    def update(idx: Int, p0: Promise[Unit], p1: Promise[Unit]): Future[Int] = {
      map.updateHandler[Int](key) { _ =>
        p0.future map { _ =>
          (_: Option[V]) =>
            p1.future map { _ =>
              val directive = MapDirective.update(idx)
              (directive, idx)
            }
        }
      }
    }

    def sequentially: SequentiallyHandler[K] = SequentiallyHandler.now
  }

  implicit class FutureOps[T](self: Future[T]) {
    def get: Option[T] = self.value map { _.get }
    def await: T = Await.result(self, 3.seconds)
  }

  private object TestException extends RuntimeException with NoStackTrace
}

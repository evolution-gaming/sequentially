package com.evolutiongaming.concurrent.sequentially

import akka.stream.Materializer
import com.evolutiongaming.concurrent.FutureHelper.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.util.control.NoStackTrace

class SequentiallyHandlerSpec extends AnyWordSpec with ActorSpec with Matchers {

  "SequentiallyHandler" should {

    "run tasks for the same key sequentially" in new Scope() {
      val p1 = Promise[Unit]()
      val p2 = Promise[Unit]()
      val p3 = Promise[Unit]()
      val p4 = Promise[Unit]()

      val result1 = sequentially.handler(0) {
        p1.future map { _ => () => p2.future }
      }

      val result2 = sequentially.handler(0) {
        p3.future map { _ => () => p4.future }
      }

      expectTimeout(result1)
      expectTimeout(result2)

      p1.success(())
      p3.success(())
      p4.success(())

      expectTimeout(result1)
      expectTimeout(result2)

      p2.success(())

      await(result1)
      await(result2)
    }

    "run tasks for different keys in parallel" in new Scope() {
      val p1 = Promise[Unit]()
      val p2 = Promise[Unit]()
      val p3 = Promise[Unit]()
      val p4 = Promise[Unit]()

      val result1 = sequentially.handler(0) {
        p1.future map { _ => () => p2.future }
      }

      val result2 = sequentially.handler(1) {
        p3.future map { _ => () => p4.future }
      }

      expectTimeout(result1)
      expectTimeout(result2)

      p1.success(())
      p3.success(())
      p4.success(())

      expectTimeout(result1)
      await(result2)

      p2.success(())

      await(result1)
    }

    "return exceptions" in new Scope {
      val promise1 = Promise[Unit]()
      val result1 = sequentially.async(1)(promise1.future)
      promise1.failure(TestException)

      the[TestException.type] thrownBy {
        await(result1)
      }

      val promise2 = Promise[Unit]()
      val result2 = sequentially.async(1)(promise2.future)
      promise2.success(())
      await(result2)

      val result3 = sequentially.async(1) { throw TestException }
      the[TestException.type] thrownBy {
        await(result3)
      }

      the[TestException.type] thrownBy {
        await(sequentially.handler(1) { throw TestException })
      }

      the[TestException.type] thrownBy {
        await(sequentially.handler(1) { Future.failed(TestException) })
      }

      the[TestException.type] thrownBy {
        await(sequentially.handler(1) { (() => Future.failed(TestException)).future })
      }
    }
  }

  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.parasitic

  private trait Scope {

    val sequentially = SequentiallyHandler[Int]()

    def expectTimeout[T](future: Future[T]): TimeoutException = {
      the[TimeoutException] thrownBy {
        Await.result(future, 100.millis)
      }
    }

    def await[T](future: Future[T]): T = {
      Await.result(future, 300.millis)
    }
  }

  case object TestException extends RuntimeException with NoStackTrace
}

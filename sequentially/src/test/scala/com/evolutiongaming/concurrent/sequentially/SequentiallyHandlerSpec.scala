package com.evolutiongaming.concurrent.sequentially

import akka.stream.ActorMaterializer
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.util.control.NoStackTrace

class SequentiallyHandlerSpec extends WordSpec with ActorSpec with Matchers {

  "SequentiallyHandler" should {

    "run tasks for the same key sequentially" in new Scope() {
      val p1 = Promise[Unit]
      val p2 = Promise[Unit]
      val p3 = Promise[Unit]
      val p4 = Promise[Unit]

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
      val p1 = Promise[Unit]
      val p2 = Promise[Unit]
      val p3 = Promise[Unit]
      val p4 = Promise[Unit]

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
      the[TestException.type] thrownBy {
        val result = sequentially.handler(0) { throw TestException }
        Await.result(result, 100.millis)
      }
    }
  }

  implicit val materializer = ActorMaterializer()
  implicit val ec = CurrentThreadExecutionContext

  private trait Scope {

    val sequentially = SequentiallyHandler[Int]()

    def expectTimeout[T](future: Future[T]) = {
      the[TimeoutException] thrownBy {
        Await.result(future, 100.millis)
      }
    }

    def await[T](future: Future[T]) = {
      Await.result(future, 300.millis)
    }
  }

  case object TestException extends RuntimeException with NoStackTrace
}

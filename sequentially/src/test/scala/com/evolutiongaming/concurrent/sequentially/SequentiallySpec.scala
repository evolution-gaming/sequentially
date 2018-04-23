package com.evolutiongaming.concurrent.sequentially

import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class SequentiallySpec extends WordSpec with ActorSpec with Matchers with ScalaFutures {
  import system.dispatcher

  implicit val defaultPatience = PatienceConfig(5.seconds, 100.millis)

  val n = 5

  "Sequentially" should {

    "run sequentially for key" in new Scope {

      val futures = for {_ <- 0 to n} yield Future {
        val futures = for {key <- 0 to n} yield sequentially(key) { Thread.sleep(1); key }
        Future sequence futures
      } flatMap identity

      for {
        xs <- (Future sequence futures).futureValue
      } xs shouldEqual xs.sorted
    }

    "run in parallel for different keys" in new Scope {
      val promise = Promise[Int]
      val future1 = sequentially(0) { await(promise.future) }
      val future2 = sequentially(0) { 2 }
      val future3 = sequentially(1) { 3 }
      future3.futureValue shouldEqual 3
      future1.value shouldEqual None
      future2.value shouldEqual None
      promise.success(1)
      future1.futureValue shouldEqual 1
      future2.futureValue shouldEqual 2
    }

    "support case class as key" in new ActorScope  {
      case class Key(value: String)

      val sequentially: Sequentially[Key] = Sequentially[Key](system)
      var actual = List.empty[Int]
      val expected = (0 to 10).toList

      val futures = for {
        x <- expected
      } yield {
        sequentially(Key("key")) {
          actual = x :: actual
        }
      }

      Future.sequence(futures).futureValue

      actual.reverse shouldEqual expected
    }

    "handle exceptions" in new Scope {
      intercept[RuntimeException] {
        sequentially(0) { throw new RuntimeException() }.futureValue
      }
      sequentially(0)("").futureValue shouldEqual ""
    }

    "handle any key" in new Scope {
      val expected = (0 to 100).toSet
      val futures = for { key <- expected} yield sequentially(key) { key }
      val actual = Future.sequence(futures).futureValue
      actual shouldEqual expected
    }
  }

  "Sequentially stream based" should {

    "run sequentially for key" in new StreamScope {

      val futures = for {_ <- 0 to n} yield Future {
        val futures = for {key <- 0 to n} yield sequentially(key) { Thread.sleep(1); key }
        Future sequence futures
      } flatMap identity

      for {
        xs <- (Future sequence futures).futureValue
      } xs shouldEqual xs.sorted
    }

    "run in parallel for different keys" in new StreamScope {
      val promise = Promise[Int]
      val future1 = sequentially(0) { await(promise.future) }
      val future2 = sequentially(0) { 2 }
      val future3 = sequentially(1) { 3 }
      future3.futureValue shouldEqual 3
      future1.value shouldEqual None
      future2.value shouldEqual None
      promise.success(1)
      future1.futureValue shouldEqual 1
      future2.futureValue shouldEqual 2
    }

    "handle exceptions" in new StreamScope {
      intercept[RuntimeException] {
        sequentially(0) { throw new RuntimeException() }.futureValue
      }
      sequentially(0)("").futureValue shouldEqual ""
    }
  }

  private trait Scope extends ActorScope {
    val sequentially: Sequentially[Int] = Sequentially[Int](system)
  }

  private trait StreamScope extends ActorScope {
    implicit val materializer = ActorMaterializer()
    val sequentially: Sequentially[Int] = Sequentially[Int]()
  }

  def await[T](future: Future[T]) = {
    Await.result(future, 5.seconds)
  }
}

package com.evolutiongaming.concurrent.sequentially

import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future
import scala.concurrent.duration._

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

    "handle exceptions" in new Scope {
      intercept[RuntimeException] {
        sequentially(0) { throw new RuntimeException() }.futureValue
      }
      sequentially(0)("").futureValue shouldEqual ""
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
}

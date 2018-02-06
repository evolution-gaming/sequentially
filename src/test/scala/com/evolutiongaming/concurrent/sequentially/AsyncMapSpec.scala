package com.evolutiongaming.concurrent.sequentially

import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.{Future, Promise}
import scala.util.Success

class AsyncMapSpec extends WordSpec with Matchers {

  implicit val ec = CurrentThreadExecutionContext

  "AsyncMap" should {

    "return values" in new Scope {
      map.values shouldEqual Map()
      map.put(0, "")
      map.values shouldEqual Map(0 -> "")
    }

    "update when state before is None" in new Scope {
      val result = map.update(0) { before =>
        (MapDirective.update(""), before)
      }
      result.value shouldEqual Some(Success(None))
      map.get(0) shouldEqual Some("")
    }

    "update when state before is Some" in new Scope {
      map.put(0, "")
      val result = map.update(0) { before =>
        (MapDirective.remove, before)
      }
      result.value shouldEqual Some(Success(Some("")))
      map.get(0) shouldEqual None
    }

    "ignore" in new Scope {
      val result = map.update(0) { before =>
        (MapDirective.ignore, before)
      }
      result.value shouldEqual Some(Success(None))
      map.get(0) shouldEqual None
    }

    "remove" in new Scope {
      map.remove(0).value shouldEqual Some(Success(None))
      map.put(0, "")
      map.remove(0).value shouldEqual Some(Success(Some("")))
      map.get(0) shouldEqual None
    }

    "update async" in new Scope {
      map.updateAsync(0) { before =>
        val directive = MapDirective.update("")
        val result = (directive, before)
        Future.successful(result)
      }.value shouldEqual Some(Success(None))

      map.get(0) shouldEqual Some("")

      val promise = Promise[MapDirective[String]]
      val result = map.updateAsync(0) { before =>
        promise.future map { directive =>
          (directive, before)
        }
      }

      result.value shouldEqual None
      map.get(0) shouldEqual Some("")
      promise.success(MapDirective.update(" "))
      result.value shouldEqual Some(Success(Some("")))
      map.get(0) shouldEqual Some(" ")
    }
  }

  private trait Scope {
    val map = AsyncMap[Int, String](SequentiallyAsync.now)
  }
}


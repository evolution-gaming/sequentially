package com.evolutiongaming.concurrent.sequentially

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class SequentialMapSpec extends AnyWordSpec with Matchers {

  "SequentialMap" should {

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
      map.getNow(0) shouldEqual Some("")
    }

    "update when state before is Some" in new Scope {
      map.put(0, "")
      val result = map.update(0) { before =>
        (MapDirective.remove, before)
      }
      result.value shouldEqual Some(Success(Some("")))
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
      map.put(0, "")
      map.remove(0).value shouldEqual Some(Success(Some("")))
      map.getNow(0) shouldEqual None
    }

    "getOrUpdate" in new Scope {
      map.getOrUpdate(0)("a").value shouldEqual Some(Success("a"))
      map.getOrUpdate(0)("b").value shouldEqual Some(Success("a"))
    }

    "updateUnit" in new Scope {
      map.updateUnit(0) { _ => MapDirective.Update("a") }
      map.getNow(0) shouldEqual Some("a")
    }

    "get" in new Scope {
      map.get(0).value shouldEqual Some(Success(None))
      map.put(0, "a")
      map.get(0).value shouldEqual Some(Success(Some("a")))
    }
  }

  private trait Scope {
    val map: SequentialMap[Int, String] = SequentialMap[Int, String](Sequentially.now)
  }
}

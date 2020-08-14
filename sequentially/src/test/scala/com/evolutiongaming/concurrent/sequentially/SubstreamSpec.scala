package com.evolutiongaming.concurrent.sequentially

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SubstreamSpec extends AnyFunSuite with Matchers {
  for {
    (key, substream) <- List((0, 0), (1, 1), (10, 0), (Int.MaxValue, 7), (Int.MinValue, 8))
  } {
    test(s"return $substream for $key") {
      Substream(key, 10) shouldEqual substream
    }
  }
}

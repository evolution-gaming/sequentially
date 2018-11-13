package com.evolutiongaming.concurrent.sequentially

import org.scalatest.{FunSuite, Matchers}

class SubstreamSpec extends FunSuite with Matchers {
  for {
    (key, substream) <- List(
      (0, 0),
      (1, 1),
      (10, 0),
      (Int.MaxValue, 7),
      (Int.MinValue, 8))
  } {
    test(s"return $substream for $key") {
      Substream(key, 10) shouldEqual substream
    }
  }
}

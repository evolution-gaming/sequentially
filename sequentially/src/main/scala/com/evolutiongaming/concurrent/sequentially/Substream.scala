package com.evolutiongaming.concurrent.sequentially

object Substream {
  def apply[T](key: T, substreams: Int): Int = {
    math.abs(key.hashCode() % substreams)
  }
}

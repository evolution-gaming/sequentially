package com.evolutiongaming.concurrent.sequentially

object Substream {
  def apply[T](key: T, substreams: Int): Int = {
    val hash = math.abs(key.hashCode())
    hash % substreams
  }
}
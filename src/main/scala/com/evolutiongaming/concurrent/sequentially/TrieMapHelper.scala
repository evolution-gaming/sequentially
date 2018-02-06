package com.evolutiongaming.concurrent.sequentially

import scala.collection.concurrent.TrieMap

object TrieMapHelper {

  implicit class TrieMapOps[K, V](val self: TrieMap[K, V]) extends AnyVal {

    def apply(key: K, directive: MapDirective[V]): Unit = {
      directive match {
        case MapDirective.Update(value) => self.put(key, value)
        case MapDirective.Remove        => self.remove(key)
        case MapDirective.Ignore        =>
      }
    }
  }
}

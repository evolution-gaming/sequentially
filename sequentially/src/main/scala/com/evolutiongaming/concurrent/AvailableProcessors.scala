package com.evolutiongaming.concurrent

object AvailableProcessors {

  private lazy val value = Runtime.getRuntime.availableProcessors

  def apply(): Int = value
}

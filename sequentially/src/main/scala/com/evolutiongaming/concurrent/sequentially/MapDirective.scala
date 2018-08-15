package com.evolutiongaming.concurrent.sequentially


sealed trait MapDirective[+T]

object MapDirective {

  def update[T](newValue: T): MapDirective[T] = Update(newValue)

  def remove[T]: MapDirective[T] = Remove

  def ignore[T]: MapDirective[T] = Ignore

  
  final case class Update[+T](newValue: T) extends MapDirective[T]

  case object Remove extends MapDirective[Nothing]

  case object Ignore extends MapDirective[Nothing]
}

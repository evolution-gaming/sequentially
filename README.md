# Sequentially
[![Build Status](https://github.com/evolution-gaming/sequentially/workflows/CI/badge.svg)](https://github.com/evolution-gaming/sequentially/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/evolution-gaming/sequentially/badge.svg)](https://coveralls.io/r/evolution-gaming/sequentially)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/31ef1c904dae40d992d9537adfdad73e)](https://www.codacy.com/app/evolution-gaming/sequentially?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=evolution-gaming/sequentially&amp;utm_campaign=Badge_Grade)
[![Version](https://img.shields.io/badge/version-click-blue)](https://evolution.jfrog.io/artifactory/api/search/latestVersion?g=com.evolutiongaming&a=prometheus-tools_2.13&repos=public)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

This library contains `Sequentially.scala` which allows running tasks sequentially for the same key and in parallel for different keys
The behavior is somehow similar to what actors propose, however it provides typesafety.
Also, it is easy to write tests using `Sequentially.now` to avoid an unnecessary concurrency.   

```scala
trait Sequentially[-K] {
  def apply[KK <: K, T](key: KK)(task: => T): Future[T]
}
```

## Example

This example explains how we can ensure that there are no concurrent updates to `var state`

```scala
type Key = Unit // ok for example
val system = ActorSystem() // yes, we have dependency on akka
val sequentially = Sequentially[Key](system)

var state: Int = 0

// this runs sequentially, like message handling in actors 
sequentially(()) {
 state = state + 1
}

// you also can expose computation result as Future[T]
val stateBefore: Future[Int] = sequentially(()) {
  val stateBefore = state
  state = state + 1
  stateBefore
} 
```

## Other good stuff

We usually have more complicated requirements in real life applications, 
thus we have implemented  more powerful `Sequentially-like` structures

```scala
trait SequentiallyAsync[-K] extends Sequentially[K] {

  def async[KK <: K, T](key: K)(task: => Future[T]): Future[T]
}
```

```scala
trait SequentiallyHandler[-K] extends SequentiallyAsync[K] {

  def handler[KK <: K, T](key: KK)(task: => Future[() => Future[T]]): Future[T]
}
```

And also we have mixed `TrieMap` with `Sequentially`, results can be found in

* AsyncMap.scala
* SequentialMap.scala
* AsyncHandlerMap.scala      

  
## Setup

```scala
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

libraryDependencies += "com.evolutiongaming" %% "sequentially" % "1.0.0"
```
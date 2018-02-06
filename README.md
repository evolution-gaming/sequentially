# Sequentially [![Build Status](https://travis-ci.org/evolution-gaming/sequentially.svg)](https://travis-ci.org/evolution-gaming/sequentially) [![Coverage Status](https://coveralls.io/repos/evolution-gaming/sequentially/badge.svg)](https://coveralls.io/r/evolution-gaming/sequentially) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/31ef1c904dae40d992d9537adfdad73e)](https://www.codacy.com/app/evolution-gaming/sequentially?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=evolution-gaming/sequentially&amp;utm_campaign=Badge_Grade) [ ![version](https://api.bintray.com/packages/evolutiongaming/maven/sequentially/images/download.svg) ](https://bintray.com/evolutiongaming/maven/sequentially/_latestVersion)

This library contains `Sequentially.scala` which allows to run tasks sequentially for the same key and in parallel for different keys
The behavior is somehow similar to what actors propose, however it provides typesafety.
Also it is easy to write tests using `Sequentially.now` to avoid unnecessary concurrency.   

```scala
trait Sequentially[-K] {
  def apply[KK <: K, T](key: KK)(task: => T): Future[T]
}
```

## Example

This example explains how we can ensure that there are no concurrent updates to `var state`

```scala
type Key = Unit // fine in example
val system = ActorSystem() // yes we have dependency on akka
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
resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

libraryDependencies += "com.evolutiongaming" %% "sequentially" % "1.0.0"
```
package com.evolutiongaming.concurrent.sequentially

import akka.actor.{Actor, ActorRefFactory, Props}
import akka.routing.ConsistentHashingPool
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import com.evolutiongaming.concurrent.sequentially.SourceQueueHelper._
import com.evolutiongaming.concurrent.{AvailableProcessors, CurrentThreadExecutionContext}

import scala.concurrent.{Future, Promise}
import scala.util.Try

/**
  * Runs tasks sequentially for the same key and in parallel - for different keys
  */
trait Sequentially[-K] {

  def apply[KK <: K, T](key: KK)(task: => T): Future[T]

  def comap[KK <: K, T](f: T => KK): Sequentially[T] = new Sequentially.Comap(f, this)
}

object Sequentially {

  lazy val Substreams: Int = AvailableProcessors() * 10
  lazy val BufferSize: Int = Int.MaxValue


  def apply[K](factory: ActorRefFactory): Sequentially[K] = {
    apply(factory, None, Substreams)
  }

  def apply[K](factory: ActorRefFactory, name: Option[String]): Sequentially[K] = {
    apply(factory, name, Substreams)
  }

  def apply[K](factory: ActorRefFactory, name: Option[String], substreams: Int): Sequentially[K] = {

    case class Task(consistentHashKey: K, task: () => Unit) extends ConsistentHashable

    def actor = new Actor {
      def receive: Receive = { case Task(_, task) => task() }
    }

    val props = Props(actor) withRouter ConsistentHashingPool(substreams)
    val ref = name map { name => factory.actorOf(props, name) } getOrElse factory.actorOf(props)

    new Sequentially[K] {
      def apply[KK <: K, T](key: KK)(task: => T): Future[T] = {
        val promise = Promise[T]
        val safeTask: () => Unit = () => promise tryComplete Try(task)
        ref ! Task(key, safeTask)
        promise.future
      }
    }
  }


  def apply[K](
    substreams: Int = Substreams,
    bufferSize: Int = BufferSize,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure)
    (implicit materializer: Materializer): Sequentially[K] = {

    val queue = Source
      .queue[Elem](bufferSize, overflowStrategy)
      .groupBy(substreams, _.substream)
      .buffer(bufferSize, OverflowStrategy.backpressure)
      .mapAsync(1) { _.apply() }
      .to(Sink.ignore)
      .run()(materializer)

    implicit val ecNow = CurrentThreadExecutionContext
    val ec = materializer.executionContext

    case class Elem(substream: Int, apply: () => Future[Any])

    new Sequentially[K] {
      def apply[KK <: K, T](key: KK)(task: => T): Future[T] = {
        val promise = Promise[T]
        val safeTask = () => {
          val result = Future(task)(ec)
          promise completeWith result
          result.recover[Any] { case _ => () }
        }
        val substream = Substream(key, substreams)
        val elem = Elem(substream, safeTask)
        for {
          _ <- queue.offerOrError(elem, s"$key failed to enqueue task")
          result <- promise.future
        } yield result
      }
    }
  }


  def now[K]: Sequentially[K] = Now

  private object Now extends Sequentially[Any] {
    def apply[KK <: Any, T](key: KK)(task: => T): Future[T] = {
      Future fromTry Try(task)
    }
  }


  class Comap[A, B](tmp: A => B, sequentially: Sequentially[B]) extends Sequentially[A] {
    def apply[AA <: A, T](key: AA)(f: => T): Future[T] = sequentially(tmp(key))(f)
  }
}

object Substream {
  def apply[T](key: T, substreams: Int): Int = {
    math.abs(key.hashCode()) % substreams
  }
}
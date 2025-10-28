package com.evolutiongaming.concurrent.sequentially

import cats.effect.kernel.Async
import cats.effect.std.{Dispatcher, MapRef, Semaphore}
import cats.effect.Resource
import cats.syntax.all.*

import scala.concurrent.Future

/** Cats Effect implementation of Sequentially.
 * - Tasks for the same key are executed one-by-one (via a Semaphore(1)).
 * - Different keys run in parallel (distinct semaphores).
 * - Returns a Future[T] using a Dispatcher.
 * - Uses MapRef for thread-safe semaphore management.
 */
final class SequentiallyCats[F[_], K] private(
                                               semaphores: MapRef[F, K, Option[Option[Semaphore[F]]]],
                                               dispatcher: Dispatcher[F]
                                             )(implicit F: Async[F]) extends Sequentially[K] {

  private def getLock(key: K): F[Semaphore[F]] = {
    semaphores(key).get.flatMap {
      case Some(Some(existing)) => existing.pure[F] // Key exists and has a semaphore
      case _ => // Key doesn't exist or exists but is None
        // Create new semaphore and try to store it atomically
        Semaphore[F](1).flatMap { fresh =>
          semaphores(key).modify {
            case Some(Some(existing)) => (Some(Some(existing)), existing) // Another fiber created it first
            case _ => (Some(Some(fresh)), fresh) // We successfully created and stored it
          }
        }
    }
  }

  private def tryCleanup(key: K, sem: Semaphore[F]): F[Unit] =
    sem.available.flatMap { available =>
      if (available == 1) { // Semaphore is idle (full capacity available)
        semaphores(key).modify {
          case Some(Some(existing)) if existing == sem => (Some(None), ()) // Remove if it's the same semaphore
          case other => (other, ()) // Keep if different or already removed
        }
      } else F.unit
    }

  override def apply[KK <: K, T](key: KK)(task: => T): Future[T] = {
    dispatcher.unsafeToFuture(
      getLock(key).flatMap { sem =>
        F.bracket(
          acquire = F.unit
        )(use = _ =>
          sem.permit.use(_ => F.delay(task))
        )(release = _ =>
          tryCleanup(key, sem)
        )
      })
  }
}

object SequentiallyCats {
  
  /** Create a SequentiallyCats instance with custom semaphores and dispatcher.
   */
  def resource[F[_], K](
    semaphores: MapRef[F, K, Option[Option[Semaphore[F]]]],
    dispatcher: Dispatcher[F]
  )(implicit F: Async[F]): Resource[F, Sequentially[K]] =
    Resource.pure(new SequentiallyCats[F, K](semaphores, dispatcher))
  
  /** Create a SequentiallyCats instance with default configuration (128 shards). */
  def resource[F[_], K](implicit F: Async[F]): Resource[F, Sequentially[K]] =
    for {
      semaphores <- Resource.eval(MapRef.ofShardedImmutableMap[F, K, Option[Semaphore[F]]](128))
      disp <- Dispatcher.parallel[F]
    } yield new SequentiallyCats[F, K](semaphores, disp)
}
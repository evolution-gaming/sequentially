package com.evolutiongaming.concurrent.sequentially

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.{Dispatcher, Semaphore}
import cats.syntax.all.*

import scala.concurrent.Future

/** Cats Effect implementation of Sequentially.
  * - Tasks are distributed across fixed buckets based on key hash
  * - Tasks with the same hash bucket execute sequentially
  * - Tasks in different buckets execute in parallel
  * - Number of buckets: (availableProcessors max 1) * 5
  * - All semaphores are pre-allocated at initialization
  */
final class SequentiallyF[F[_] : Async, K] private (
  semaphores: Vector[Semaphore[F]]
) {

  private val bucketCount = semaphores.size

  /** Map key to bucket index using hash */
  private def getBucket(key: K): Int = {
    val hash = key.hashCode()
    // Use absolute value and modulo to get bucket index
    // Handle Integer.MIN_VALUE edge case
    math.abs(hash % bucketCount)
  }

  /** Execute a by-name task and return a Future.
    * This implements the Sequentially trait contract.
    *
    * @param key  the key for sequential execution
    * @param task the by-name task to execute
    * @return Future[T] result
    */
  def apply[KK <: K, T](
    key: KK
  )(
    task: => T
  )(implicit
    dispatcher: Dispatcher[F]
  ): Future[T] = {
    val semaphore = semaphores(getBucket(key))
    val run: F[T] = semaphore.permit.use(_ => Async[F].delay(task))
    dispatcher.unsafeToFuture(run)
  }

  /** Execute a task that returns F[T] and stay in the F context.
    * This is more efficient than the Future-based apply when you're already working in F.
    *
    * Uses permit.use which is the idiomatic Cats Effect 3 way.
    * Note: permit returns a Resource, use() brackets acquire/release automatically.
    *
    * @param key  the key for sequential execution
    * @param task the effectful task to execute
    * @return F[T] result without Future conversion
    */
  def applyF[KK <: K, T](key: KK)(task: => F[T]): F[T] = {
    semaphores(getBucket(key)).permit.use(_ => task)
  }
}

object SequentiallyF {

  /** Create a SequentiallyF instance with custom semaphores and dispatcher.
    * Useful for testing.
    */
  def resource[F[_], K](
    semaphores: Vector[Semaphore[F]]
  )(implicit
    F: Async[F]
  ): Resource[F, SequentiallyF[F, K]] =
    Resource.pure(new SequentiallyF[F, K](semaphores))

  /** Create a SequentiallyF instance with default configuration.
    * Bucket count = (availableProcessors max 1) * 5
    */
  def resource[F[_], K](
    implicit
    F: Async[F]
  ): Resource[F, SequentiallyF[F, K]] = {
    val bucketCount = (Runtime.getRuntime.availableProcessors() max 1) * 5

    for {
      // Pre-allocate all semaphores
      semaphores <- Resource.eval(
        List.fill(bucketCount)(Semaphore[F](1)).sequence.map(_.toVector)
      )
    } yield new SequentiallyF[F, K](semaphores)
  }
}

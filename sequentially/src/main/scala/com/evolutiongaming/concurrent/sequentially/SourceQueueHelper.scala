package com.evolutiongaming.concurrent.sequentially

import akka.stream.QueueOfferResult as Result
import akka.stream.scaladsl.SourceQueue

import scala.concurrent.{ExecutionContext, Future}

object SourceQueueHelper {

  implicit class SourceQueueOps[T](val self: SourceQueue[T]) extends AnyVal {

    def offerOrError(
      elem: T,
      errorMsg: => String,
    )(implicit
      ec: ExecutionContext
    ): Future[Unit] = {
      for {
        result <- self.offer(elem)
        result <- result match {
          case Result.Enqueued         => Future.unit
          case Result.Failure(failure) => Future.failed(new QueueException(errorMsg, Some(failure)))
          case failure                 => Future.failed(new QueueException(s"$errorMsg: $failure"))
        }
      } yield result
    }
  }
}

class QueueException(message: String, cause: Option[Throwable] = None)
  extends RuntimeException(message, cause.orNull)

package io.github.mercurievv.aidclimbing.checkpoint.ce

import cats.data.WriterT
import cats.syntax.all._
import cats.{Monad, Monoid, Show}
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint, Memoize}

/** Checkpoint instance for WriterT[F, W, *] that logs cache hits/misses.
 *
 *  @param memoize underlying storage in the base effect F
 *  @param writeT  produces a log entry W given (key: Any, isHit: Boolean)
 */
class WriterTCheckpoint[F[_]: Monad, W: Monoid](
  memoize: Memoize[F],
  writeT: (Any, Boolean) => W
) extends Checkpoint[WriterT[F, W, *]] {

  def checkpoint[A, K: Show, V: Show](
    checkpointId: String,
    fa: WriterT[F, W, A],
    keyFn: A => K,
    compute: A => WriterT[F, W, V]
  ): WriterT[F, W, V] =
    fa.flatMap { a =>
      val key = Checkpoint.compositeKey(checkpointId, keyFn(a))
      WriterT.liftF[F, W, Option[V]](memoize.get[String, V](key)).flatMap {
        case Some(cached) =>
          WriterT.tell[F, W](writeT(key, true)).as(cached)
        case None =>
          compute(a).flatMap { v =>
            WriterT.liftF[F, W, Unit](memoize.put(key, v))
              .flatMap(_ => WriterT.tell[F, W](writeT(key, false)))
              .as(v)
          }
      }
    }
}

object WriterTCheckpoint {
  def apply[F[_]: Monad, W: Monoid](
    memoize: Memoize[F],
    writeT: (Any, Boolean) => W
  ): Checkpoint[WriterT[F, W, *]] =
    new WriterTCheckpoint(memoize, writeT)
}
package io.github.mercurievv.aidclimbing.checkpoint.ce

import cats.{Monad, Show}
import cats.syntax.all._
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint, Memoize}

/** Checkpoint instance backed by an explicit Memoize[F, Repr]. */
class CatsCheckpoint[F[_]: Monad, Repr](memoize: Memoize[F]) extends Checkpoint[F] {

  def checkpoint[A, K: Show, V](fa: F[A], keyFn: A => K, compute: A => F[V]): F[V] =
    fa.flatMap { a =>
      val key = keyFn(a)
      memoize.get[K, V](key).flatMap {
        case Some(cached) => cached.pure[F]
        case None         => compute(a).flatTap(v => memoize.put(key, v))
      }
    }
}

object CatsCheckpoint {
  def apply[F[_]: Monad](memoize: Memoize[F]): Checkpoint[F] =
    new CatsCheckpoint(memoize)
}
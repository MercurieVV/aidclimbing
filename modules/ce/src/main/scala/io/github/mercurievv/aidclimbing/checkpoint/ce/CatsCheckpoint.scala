package io.github.mercurievv.aidclimbing.checkpoint.ce

import cats.{Monad, Show}
import cats.mtl.Tell
import cats.syntax.all._
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint, Memoize}

/** Checkpoint instance backed by an explicit Memoize[F, Repr]. */
class CatsCheckpoint[F[_]: Monad, Repr](memoize: Memoize[F])(implicit tell: Tell[F, String]) extends Checkpoint[F] {

  def checkpoint[A, K: Show, V: Show](fa: F[A], keyFn: A => K, compute: A => F[V]): F[V] =
    fa.flatMap { a =>
      val key = keyFn(a)
      memoize.get[K, V](key).flatMap {
        case Some(cached) => cached.pure[F]
        case None         => compute(a).flatTap(v => memoize.put(key, v))
      }.flatTap(v => tell.tell(v.show))
    }
}

object CatsCheckpoint {
  def apply[F[_]: Monad](memoize: Memoize[F])(implicit tell: Tell[F, String]): Checkpoint[F] =
    new CatsCheckpoint(memoize)
}
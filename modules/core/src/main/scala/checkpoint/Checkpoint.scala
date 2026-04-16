package io.github.mercurievv.aidclimbing.checkpoint

import cats.{Monad, Show}
import cats.syntax.all._

trait Checkpoint[F[_]] {
  def checkpoint[A, K: Show, V: Show](fa: F[A], keyFn: A => K, compute: A => F[V]): F[V]
}

object Checkpoint {
  def apply[F[_], Repr](implicit ev: Checkpoint[F]): Checkpoint[F] = ev

  implicit def fromMonad[F[_]: Monad](implicit m: Memoize[F]): Checkpoint[F] =
    new Checkpoint[F] {
      def checkpoint[A, K: Show, V: Show](fa: F[A], keyFn: A => K, compute: A => F[V]): F[V] =
        fa.flatMap { a =>
          val key = keyFn(a)
          m.get[K, V](key).flatMap {
            case Some(cached) => cached.pure[F]
            case None         => compute(a).flatTap(v => m.put(key, v))
          }
        }
    }
}
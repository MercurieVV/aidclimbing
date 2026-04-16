package io.github.mercurievv.aidclimbing.checkpoint.syntax

import cats.Show
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint}

trait CheckpointSyntax {
  implicit final class CheckpointOps[F[_], A](private val fa: F[A]) {
    def checkpoint[K: Show, V](keyFn: A => K, compute: A => F[V])(
      implicit C: Checkpoint[F]): F[V] = C.checkpoint(fa, keyFn, compute)
  }
}

object CheckpointSyntax extends CheckpointSyntax
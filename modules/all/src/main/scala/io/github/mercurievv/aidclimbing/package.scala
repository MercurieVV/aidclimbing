package io.github.mercurievv.aidclimbing

import io.github.mercurievv.aidclimbing.checkpoint.syntax.CheckpointSyntax

/** Single import for all checkpoint modules:
 *
 *  {{{
 *  import io.github.mercurievv.aidclimbing.checkpointall._
 *  }}}
 */
package object checkpointall extends CheckpointSyntax {

  type Checkpoint[F[_], Repr]          = checkpoint.Checkpoint[F]
  val  Checkpoint                      = checkpoint.Checkpoint

  type Memoize[F[_]]             = checkpoint.Memoize[F]

  type RefMemoize[F[_]]                = checkpoint.ce.RefMemoize[F]
  val  RefMemoize                      = checkpoint.ce.RefMemoize

  type CatsCheckpoint[F[_], Repr]      = checkpoint.ce.CatsCheckpoint[F, Repr]
  val  CatsCheckpoint                  = checkpoint.ce.CatsCheckpoint

  type WriterTCheckpoint[F[_], W] = checkpoint.ce.WriterTCheckpoint[F, W]
  val  WriterTCheckpoint               = checkpoint.ce.WriterTCheckpoint

  type FilePersister[F[_], Repr]       = checkpoint.file.FilePersister[F, Repr]
  val  FilePersister                   = checkpoint.file.FilePersister
}

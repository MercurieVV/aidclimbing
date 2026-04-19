/*
 * Copyright 2026 Viktors Kalinins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.mercurievv.aidclimbing

import io.github.mercurievv.aidclimbing.checkpoint.syntax.CheckpointSyntax

/** Single import for all checkpoint modules:
  *
  * {{{
  *  import io.github.mercurievv.aidclimbing.checkpointall._
  * }}}
  */
package object checkpointall extends CheckpointSyntax {

  type Checkpoint[F[_], Repr] = checkpoint.Checkpoint[F]
  val Checkpoint = checkpoint.Checkpoint

  type Memoize[F[_]] = checkpoint.Memoize[F]

  type RefMemoize[F[_]] = checkpoint.ce.RefMemoize[F]
  val RefMemoize = checkpoint.ce.RefMemoize

  type CatsCheckpoint[F[_], Repr] = checkpoint.ce.CatsCheckpoint[F, Repr]
  val CatsCheckpoint = checkpoint.ce.CatsCheckpoint

  type WriterTCheckpoint[F[_], W] = checkpoint.ce.WriterTCheckpoint[F, W]
  val WriterTCheckpoint = checkpoint.ce.WriterTCheckpoint

  type FileMemoize[F[_], Repr] = checkpoint.file.FileMemoize[F, Repr]
  val FileMemoize = checkpoint.file.FileMemoize

  type StreamCheckpoint[F[_]] = checkpoint.fs2.StreamCheckpoint[F]
  val StreamCheckpoint = checkpoint.fs2.StreamCheckpoint
}

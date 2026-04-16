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

package io.github.mercurievv.aidclimbing.checkpoint.syntax

import cats.Show
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint}

trait CheckpointSyntax {
  implicit final class CheckpointOps[F[_], A](private val fa: F[A]) {
    def checkpoint[K: Show, V: Show](checkpointId: String)(keyFn: A => K)(compute: A => F[V])(
      implicit C: Checkpoint[F]): F[V] = C.checkpoint(checkpointId, fa, keyFn, compute)
  }
}

object CheckpointSyntax extends CheckpointSyntax
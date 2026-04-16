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

package io.github.mercurievv.aidclimbing.checkpoint.ce

import cats.{Monad, Show}
import cats.mtl.Tell
import cats.syntax.all.*
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint, Memoize}

import scala.reflect.ClassTag

/** Checkpoint instance backed by an explicit Memoize[F, Repr]. */
class CatsCheckpoint[F[_]: Monad, Repr](memoize: Memoize[F])(implicit tell: Tell[F, String]) extends Checkpoint[F] {

  def checkpoint[A, K: Show, V: Show: ClassTag](checkpointId: String, fa: F[A], keyFn: A => K, compute: A => F[V])
    : F[V] =
    fa.flatMap { a =>
      val key = Checkpoint.compositeKey(checkpointId, keyFn(a))
      memoize
        .get[String, V](key)
        .flatMap {
          case Some(cached) => cached.pure[F]
          case None         => compute(a).flatTap(v => memoize.put(key, v))
        }
        .flatTap(v => tell.tell(v.show))
    }
}

object CatsCheckpoint {

  def apply[F[_]: Monad](memoize: Memoize[F])(implicit tell: Tell[F, String]): Checkpoint[F] =
    new CatsCheckpoint(memoize)
}

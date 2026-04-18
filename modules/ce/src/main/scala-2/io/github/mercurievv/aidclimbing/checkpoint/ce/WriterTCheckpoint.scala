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

import cats.data.WriterT
import cats.syntax.all._
import cats.{Monad, Monoid, Show}
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint, Memoize}

import scala.reflect.ClassTag

class WriterTCheckpoint[F[_]: Monad, W: Monoid](
  memoize: Memoize[F],
  writeT: (Any, Boolean) => W)
    extends Checkpoint[WriterT[F, W, *]] {

  def checkpoint[A, K: Show, V: Show: ClassTag](
    checkpointId: String,
    fa: WriterT[F, W, A],
    keyFn: A => K,
    compute: A => WriterT[F, W, V],
  ): WriterT[F, W, V] =
    fa.flatMap { a =>
      val key = Checkpoint.compositeKey(checkpointId, keyFn(a))
      WriterT.liftF[F, W, Option[V]](memoize.get[String, V](key)).flatMap {
        case Some(cached) =>
          WriterT.tell[F, W](writeT(key, true)).as(cached)
        case None =>
          compute(a).flatMap { v =>
            WriterT
              .liftF[F, W, Unit](memoize.put(key, v))
              .flatMap(_ => WriterT.tell[F, W](writeT(key, false)))
              .as(v)
          }
      }
    }
}

object WriterTCheckpoint {
  def apply[F[_]: Monad, W: Monoid](
    memoize: Memoize[F],
    writeT: (Any, Boolean) => W,
  ): Checkpoint[WriterT[F, W, *]] =
    new WriterTCheckpoint(memoize, writeT)
}

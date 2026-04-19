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

package io.github.mercurievv.aidclimbing.checkpoint.fs2

import cats.Show
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream
import io.github.mercurievv.aidclimbing.checkpoint.{Checkpoint, Memoize}

import scala.reflect.ClassTag

class StreamCheckpoint[F[_]: Concurrent](memoize: Memoize[F]) extends Checkpoint[StreamCheckpoint.StreamF[F]#L] {

  def checkpoint[A, K: Show, V: Show: ClassTag](
    checkpointId: String,
    fa: Stream[F, A],
    keyFn: A => K,
    compute: A => Stream[F, V],
  ): Stream[F, V] =
    fa.flatMap { a =>
      val key = Checkpoint.compositeKey(checkpointId, keyFn(a))
      Stream
        .eval(memoize.get[String, Vector[V]](key))
        .flatMap {
          case Some(cached) => Stream.emits(cached)
          case None         =>
            Stream.evalSeq(
              compute(a)
                .compile
                .toVector
                .flatTap(values => memoize.put(key, values)),
            )
        }
    }
}

object StreamCheckpoint {
  type StreamF[F[_]] = {
    type L[A] = Stream[F, A]
  }

  def apply[F[_]: Concurrent](memoize: Memoize[F]): StreamCheckpoint[F] =
    new StreamCheckpoint(memoize)
}

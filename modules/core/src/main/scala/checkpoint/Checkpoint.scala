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

package io.github.mercurievv.aidclimbing.checkpoint

import cats.{Monad, Show}
import cats.syntax.all.*

import scala.reflect.ClassTag

trait Checkpoint[F[_]] {
  def checkpoint[A, K: Show, V: Show: ClassTag](checkpointId: String, fa: F[A], keyFn: A => K, compute: A => F[V]): F[V]
}

object Checkpoint {
  def apply[F[_], Repr](implicit ev: Checkpoint[F]): Checkpoint[F] = ev

  def compositeKey[K: Show](checkpointId: String, key: K): String =
    s"$checkpointId::${key.show}"

  implicit def fromMonad[F[_]: Monad](implicit m: Memoize[F]): Checkpoint[F] =
    new Checkpoint[F] {
      def checkpoint[A, K: Show, V: Show: ClassTag](checkpointId: String, fa: F[A], keyFn: A => K, compute: A => F[V])
        : F[V] =
        fa.flatMap { a =>
          val key = compositeKey(checkpointId, keyFn(a))
          m.get[String, V](key).flatMap {
            case Some(cached) => cached.pure[F]
            case None         => compute(a).flatTap(v => m.put(key, v))
          }
        }
    }
}

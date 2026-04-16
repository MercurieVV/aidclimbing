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

import cats.Show

import scala.reflect.ClassTag

/** Storage abstraction for checkpoints.
 *
 *  `Repr` is the serialized form chosen by the implementation
 *  (e.g. String for text files, Array[Byte] for binary).
 *  Callers supply `Serialize.Aux[V, Repr]` / `Deserialize.Aux[V, Repr]`
 *  via smart constructors — they never write `.Aux` by hand.
 */
trait Memoize[F[_]] {
  type Repr
  def toRepr[V](v: V): Repr
  def fromRepr[V: ClassTag](repr: Repr): Either[Throwable, V]
  def get[K: Show, V: ClassTag](key: K): F[Option[V]]
  def put[K: Show, V](key: K, value: V): F[Unit]
  def delete[K: Show](key: K): F[Unit]
  def deleteAll(keyPrefix: String): F[Unit]
}

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

import cats.Show
import cats.effect.{Ref, Sync}
import cats.syntax.all._
import io.github.mercurievv.aidclimbing.checkpoint.Memoize

import scala.reflect.ClassTag

/** In-memory Memoize backed by a cats-effect Ref.
 *
 *  Repr = Any: values are stored without serialization (same JVM, type preserved).
 *  Retrieved values are checked against the requested runtime type.
 */
class RefMemoize[F[_]: Sync] private (ref: Ref[F, Map[String, Any]])
    extends Memoize[F] {

  type Repr = Any

  override def toRepr[V](v: V): Any = v

  override def fromRepr[V: ClassTag](repr: Any): Either[Throwable, V] =
    repr match {
      case value: V => Right(value)
      case _        =>
        Left(new ClassCastException(s"Expected ${implicitly[ClassTag[V]].runtimeClass.getName}, got ${repr.getClass.getName}"))
    }

  def get[K: Show, V: ClassTag](key: K): F[Option[V]] =
    ref.get.map(_.get(key.show)).flatMap {
      case None       => none[V].pure[F]
      case Some(repr) => Sync[F].fromEither(fromRepr[V](repr).map(_.some))
    }

  def put[K: Show, V](key: K, value: V): F[Unit] =
    ref.update(_ + (key.show -> toRepr(value)))

  def delete[K: Show](key: K): F[Unit] =
    ref.update(_ - key.show)

  def deleteAll(keyPrefix: String): F[Unit] =
    ref.update(_.view.filterKeys(!_.startsWith(keyPrefix)).toMap)
}

object RefMemoize {
  def make[F[_]: Sync]: F[Memoize[F]] =
    Ref.of[F, Map[String, Any]](Map.empty).map(new RefMemoize(_))
}

package io.github.mercurievv.aidclimbing.checkpoint

import cats.Show

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
  def fromRepr[V](repr: Repr): Either[Throwable, V]
  def get[K: Show, V](key: K): F[Option[V]]
  def put[K: Show, V](key: K, value: V): F[Unit]
  def delete[K: Show](key: K): F[Unit]
  def deleteAll(keyPrefix: String): F[Unit]
}
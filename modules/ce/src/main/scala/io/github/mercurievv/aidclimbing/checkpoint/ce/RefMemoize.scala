package io.github.mercurievv.aidclimbing.checkpoint.ce

import cats.Show
import cats.effect.{Ref, Sync}
import cats.syntax.all._
import io.github.mercurievv.aidclimbing.checkpoint.Memoize

import scala.util.Try

/** In-memory Memoize backed by a cats-effect Ref.
 *
 *  Repr = Any: values are stored without serialization (same JVM, type preserved).
 *  Retrieved values are cast back — safe as long as the same K always maps to the same V type.
 */
class RefMemoize[F[_]: Sync] private (ref: Ref[F, Map[String, Any]])
    extends Memoize[F] {

  type Repr = Any

  override def toRepr[V](v: V): Any = v

  override def fromRepr[V](repr: Any): Either[Throwable, V] =
    Try(repr.asInstanceOf[V]).toEither

  def get[K: Show, V](key: K): F[Option[V]] =
    ref.get.map(_.get(key.show)).flatMap {
      case None       => none[V].pure[F]
      case Some(repr) => Sync[F].fromEither(fromRepr[V](repr).map(_.some))
    }

  def put[K: Show, V](key: K, value: V): F[Unit] =
    ref.update(_ + (key.show -> toRepr(value)))

  def delete[K: Show](key: K): F[Unit] =
    ref.update(_ - key.show)

  def deleteAll(keyPrefix: String): F[Unit] =
    ref.update(_.filterKeys(!_.startsWith(keyPrefix)).toMap)
}

object RefMemoize {
  def make[F[_]: Sync]: F[Memoize[F]] =
    Ref.of[F, Map[String, Any]](Map.empty).map(new RefMemoize(_))
}
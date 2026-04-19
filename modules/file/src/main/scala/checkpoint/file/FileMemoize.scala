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

package io.github.mercurievv.aidclimbing.checkpoint.file

import cats.Show
import cats.effect.Async
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.{Files, Path}
import io.github.mercurievv.aidclimbing.checkpoint.Memoize

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag
import scala.util.Try

/** File-system Memoize.
  *
  * @param dir
  *   directory to store checkpoint files
  * @param writeFile
  *   persists a Repr to a Path
  * @param readFile
  *   reads a Repr from a Path
  */
abstract class FileMemoize[F[_]: Async: Files, R](
  dir: Path,
  writeFile: (Path, R) => F[Unit],
  readFile: Path => F[R])
    extends Memoize[F] {
  override type Repr = R

  private def keyPath[K: Show](key: K): Path =
    dir / FileMemoize.encodeKey(key.show)

  override def get[K: Show, V: ClassTag](key: K): F[Option[V]] = {
    val p = keyPath(key)
    Files[F].exists(p).flatMap {
      case false => none[V].pure[F]
      case true  =>
        readFile(p).flatMap { repr =>
          Async[F].fromEither(fromRepr(repr).map(_.some))
        }
    }
  }

  override def put[K: Show, V](key: K, value: V): F[Unit] = {
    val p = keyPath(key)
    Files[F].createDirectories(p.parent.getOrElse(dir)) >> writeFile(p, toRepr(value))
  }

  override def delete[K: Show](key: K): F[Unit] =
    Files[F].deleteIfExists(keyPath(key)).void

  override def deleteAll(keyPrefix: String): F[Unit] =
    Files[F]
      .list(dir)
      .filter(_.fileName.toString.startsWith(FileMemoize.encodeKey(keyPrefix)))
      .evalMap(Files[F].deleteIfExists(_).void)
      .compile
      .drain

}

object FileMemoize {

  private val SafeCharPattern = "[A-Za-z0-9._-]".r

  private[file] def encodeKey(key: String): String = {
    val builder = new StringBuilder
    key.foreach { ch =>
      if (SafeCharPattern.pattern.matcher(ch.toString).matches()) builder += ch
      else {
        val bytes = ch.toString.getBytes(StandardCharsets.UTF_8)
        bytes.foreach { byte =>
          builder  += '%'
          builder ++= f"${byte & 0xff}%02X"
        }
      }
    }
    builder.result()
  }

  def string[F[_]: Async: Files](dir: Path): Memoize[F] =
    new FileMemoize[F, String](
      dir,
      writeFile = (p, s) => Stream.emit(s).through(fs2.text.utf8.encode).through(Files[F].writeAll(p)).compile.drain,
      readFile  = p => Files[F].readAll(p).through(fs2.text.utf8.decode).compile.string,
    ) {
      override def toRepr[V](v: V): String = v.toString

      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def fromRepr[V: ClassTag](repr: String): Either[Throwable, V] =
        Try {
          (implicitly[ClassTag[V]].runtimeClass match {
            case c if c == classOf[String]  => repr
            case c if c == classOf[Int]     => repr.toInt
            case c if c == classOf[Long]    => repr.toLong
            case c if c == classOf[Double]  => repr.toDouble
            case c if c == classOf[Boolean] => repr.toBoolean
            case _                          => repr
          }).asInstanceOf[V]
        }.toEither
    }
}

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
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream, Serializable}
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

  private val SafeCharPattern = "[/A-Za-z0-9._-]".r

  private[file] def encodeKey(key: String): String =
    key.foldLeft("") { (acc, ch) =>
      val chStr = ch.toString
      if (SafeCharPattern.pattern.matcher(chStr).matches()) acc + chStr
      else
        scala.collection.immutable.ArraySeq
          .unsafeWrapArray(chStr.getBytes(StandardCharsets.UTF_8))
          .foldLeft(acc)((a, byte) => a + "%" + f"${byte & 0xff}%02X")
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

  def circe[F[_]: Async: Files, V: Encoder: Decoder: ClassTag](dir: Path): Memoize[F] =
    new FileMemoize[F, String](
      dir,
      writeFile = (p, s) => Stream.emit(s).through(fs2.text.utf8.encode).through(Files[F].writeAll(p)).compile.drain,
      readFile  = p => Files[F].readAll(p).through(fs2.text.utf8.decode).compile.string,
    ) {
      @SuppressWarnings(Array("org.wartremover.warts.Throw"))
      override def toRepr[A](value: A): String =
        value match {
          case typed: V => typed.asJson.noSpaces
          case _        =>
            throw new ClassCastException(
              s"Expected ${implicitly[ClassTag[V]].runtimeClass.getName}, got ${value.getClass.getName}",
            )
        }

      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def fromRepr[A: ClassTag](repr: String): Either[Throwable, A] =
        if (implicitly[ClassTag[A]].runtimeClass == implicitly[ClassTag[V]].runtimeClass)
          decode[V](repr).map(_.asInstanceOf[A])
        else
          Left(
            new ClassCastException(
              s"Expected ${implicitly[ClassTag[V]].runtimeClass.getName}, got ${implicitly[ClassTag[A]].runtimeClass.getName}",
            ),
          )
    }

  def javaSerialization[F[_]: Async: Files](dir: Path): Memoize[F] =
    new FileMemoize[F, Array[Byte]](
      dir,
      writeFile = (p, bytes) => Stream.emits(bytes).covary[F].through(Files[F].writeAll(p)).compile.drain,
      readFile  = p => Files[F].readAll(p).compile.to(Array),
    ) {
      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def toRepr[V](value: V): Array[Byte] = {
        val output = new ByteArrayOutputStream()
        val objectOutput = new ObjectOutputStream(output)
        try {
          objectOutput.writeObject(value.asInstanceOf[Serializable])
          objectOutput.flush()
          output.toByteArray
        } finally {
          objectOutput.close()
          output.close()
        }
      }

      override def fromRepr[V: ClassTag](repr: Array[Byte]): Either[Throwable, V] =
        Try {
          val input = new ByteArrayInputStream(repr)
          val objectInput = new ObjectInputStream(input)
          try objectInput.readObject()
          finally {
            objectInput.close()
            input.close()
          }
        }.toEither.flatMap { value =>
          value match {
            case typed: V => Right(typed)
            case _        =>
              Left(
                new ClassCastException(
                  s"Expected ${implicitly[ClassTag[V]].runtimeClass.getName}, got ${value.getClass.getName}",
                ),
              )
          }
        }
    }
}

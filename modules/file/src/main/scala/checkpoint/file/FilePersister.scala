package io.github.mercurievv.aidclimbing.checkpoint.file

import cats.Show
import cats.effect.Async
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.{Files, Path}
import io.github.mercurievv.aidclimbing.checkpoint.{Memoize}

/** File-system Memoize.
 *
 *  @param dir       directory to store checkpoint files
 *  @param writeFile persists a Repr to a Path
 *  @param readFile  reads a Repr from a Path
 */
class FilePersister[F[_]: Async, R](
  dir: Path,
  writeFile: (Path, R) => F[Unit],
  readFile: Path => F[R]
) extends Memoize[F] {
  override type Repr = R

  override def get[K: Show, V](key: K): F[Option[V]] = {
    val p = dir / key.show
    Files[F].exists(p).flatMap {
      case false => none[V].pure[F]
      case true  =>
        readFile(p).flatMap { repr =>
          Async[F].fromEither(fromRepr(repr))
        }
    }
  }

  override def put[K: Show, V](key: K, value: V): F[Unit] = {
    val p = dir / key.show
    Files[F].createDirectories(dir) >> writeFile(p, toRepr(value))
  }

  override def delete[K: Show](key: K): F[Unit] =
    Files[F].deleteIfExists(dir / key.show).void

  override def deleteAll(keyPrefix: String): F[Unit] =
    Files[F].list(dir)
      .filter(_.fileName.toString.startsWith(keyPrefix))
      .evalMap(Files[F].deleteIfExists(_).void)
      .compile
      .drain

  override def toRepr[V](v: V): R = ???

  override def fromRepr[V](repr: R): Either[Throwable, V] = ???
}

object FilePersister {
  def apply[F[_]: Async, Repr](
    dir: Path,
    writeFile: (Path, Repr) => F[Unit],
    readFile: Path => F[Repr]
  ): Memoize[F] =
    new FilePersister(dir, writeFile, readFile)

  def string[F[_]: Async](dir: Path): Memoize[F] =
    new FilePersister[F, String](
      dir,
      writeFile = (p, s) =>
        Stream.emit(s).through(fs2.text.utf8.encode).through(Files[F].writeAll(p)).compile.drain,
      readFile = p =>
        Files[F].readAll(p).through(fs2.text.utf8.decode).compile.string
    )
}
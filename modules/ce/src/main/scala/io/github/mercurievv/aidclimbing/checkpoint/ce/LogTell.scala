package io.github.mercurievv.aidclimbing.checkpoint.ce

import cats.Functor
import cats.effect.Sync
import cats.mtl.Tell
import cats.syntax.all._
import org.typelevel.log4cats.Logger

object LogTell {
  def apply[F[_]: Sync](implicit logger: Logger[F]): Tell[F, String] =
    new Tell[F, String] {
      def tell(s: String): F[Unit] =
        Sync[F].delay(println(s)) >> logger.info(s)

      override def functor: Functor[F] = implicitly
    }
}
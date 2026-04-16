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
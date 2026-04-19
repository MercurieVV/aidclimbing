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

package io.github.mercurievv.aidclimbing

import cats.effect.IO
import fs2.io.file.{Files, Path}
import io.github.mercurievv.aidclimbing.checkpoint.file.FileMemoize
import io.circe.{Decoder, Encoder}
import munit.CatsEffectSuite

import java.io.Serializable
import scala.concurrent.duration.DurationInt

object FileMemoizeSpec {
  final case class JsonValue(name: String, count: Int)

  object JsonValue {

    implicit val encoder: Encoder[JsonValue] =
      Encoder.forProduct2("name", "count")(value => (value.name, value.count))

    implicit val decoder: Decoder[JsonValue] =
      Decoder.forProduct2("name", "count")(JsonValue.apply)
  }

  final case class JavaValue(name: String, count: Int) extends Serializable
}

class FileMemoizeSpec extends CatsEffectSuite {
  import FileMemoizeSpec._

  val tempDir: Fixture[Path] = ResourceSuiteLocalFixture(
    "tempDir",
    Files[IO].tempDirectory,
  )

  override def munitFixtures = List(tempDir)

  private def memoize = FileMemoize.string[IO](tempDir())

  test("String roundtrips") {
    val m = memoize
    m.put("k", "hello") >>
      m.get[String, String]("k").map(v => assertEquals(v, Some("hello")))
  }

  test("Int roundtrips") {
    val m = memoize
    m.put("k", 42) >>
      m.get[String, Int]("k").map(v => assertEquals(v, Some(42)))
  }

  test("Long roundtrips") {
    val m = memoize
    m.put("k", 123L) >>
      m.get[String, Long]("k").map(v => assertEquals(v, Some(123L)))
  }

  test("Double roundtrips") {
    val m = memoize
    m.put("k", 3.14) >>
      m.get[String, Double]("k").map(v => assertEquals(v, Some(3.14)))
  }

  test("Boolean roundtrips") {
    val m = memoize
    m.put("k", true) >>
      m.get[String, Boolean]("k").map(v => assertEquals(v, Some(true)))
  }

  test("missing key returns None") {
    memoize.get[String, String]("no-such-key").map(v => assertEquals(v, None))
  }

  test("delete removes entry") {
    val m = memoize
    m.put("k", "x") >>
      m.delete("k") >>
      m.get[String, String]("k").map(v => assertEquals(v, None))
  }

  test("keys containing path separators do not hang and roundtrip") {
    val m = memoize
    val key = "clear-frames-dir::./frames"

    m.put(key, "done")
      .timeout(2.seconds)
      .flatMap(_ => m.get[String, String](key).timeout(2.seconds))
      .map(v => assertEquals(v, Some("done")))
  }

  test("Circe memoize serializes and deserializes values") {
    val m = FileMemoize.circe[IO, JsonValue](tempDir())
    val value = JsonValue("hello", 42)

    m.put("json", value) >>
      m.get[String, JsonValue]("json").map(v => assertEquals(v, Some(value)))
  }

  test("Java serialization memoize serializes and deserializes values") {
    val m = FileMemoize.javaSerialization[IO](tempDir())
    val value = JavaValue("hello", 42)

    m.put("java", value) >>
      m.get[String, JavaValue]("java").map(v => assertEquals(v, Some(value)))
  }
}

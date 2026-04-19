# Getting Started

## SBT project

Add the dependency:

```scala
libraryDependencies += "io.github.mercurievv.aidclimbing" %% "checkpoint-all" % "@AIDCLIMBING_VERSION@"
```

## scala-cli

For scripts and one-off research, add directives at the top of your `.sc` file:

```scala
//> using dep "io.github.mercurievv.aidclimbing::checkpoint-all:@AIDCLIMBING_VERSION@"
//> using dep "org.typelevel::log4cats-noop:2.6.0"
```

Run with:

```
scala-cli pipeline.sc
```

## Basic example

`FileMemoize` persists checkpoint results to a directory. Same input key → cached result; `compute` is skipped.

```scala mdoc
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.mtl.Tell
import fs2.io.file.Path
import io.github.mercurievv.aidclimbing.checkpointall._
import io.github.mercurievv.aidclimbing.checkpoint.Checkpoint
import io.github.mercurievv.aidclimbing.checkpoint.ce.LogTell
import io.github.mercurievv.aidclimbing.checkpoint.file.FileMemoize
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

implicit val logger: Logger[IO] = NoOpLogger[IO]
implicit val teller: Tell[IO, String] = LogTell[IO]
```

```scala mdoc
val cacheDir = Path(java.nio.file.Files.createTempDirectory("aidclimbing-").toString)

val program = {
  val memoize = FileMemoize.string[IO](cacheDir)
  implicit val cp: Checkpoint[IO] = CatsCheckpoint(memoize)

  for {
    callCount <- Ref.of[IO, Int](0)
    expensive  = (s: String) => callCount.update(_ + 1) *> IO.pure(s.length)
    first     <- cp.checkpoint("step1", IO("hello"), identity[String], expensive)
    second    <- cp.checkpoint("step1", IO("hello"), identity[String], expensive)
    count     <- callCount.get
  } yield (first, second, count)
}
```

```scala mdoc
program.unsafeRunSync()
// (5, 5, 1) — compute ran once, second call returned cached result
```

## Multi-stage pipeline

Only stages whose input key changes re-execute:

```scala mdoc
val pipeline = {
  val memoize = FileMemoize.string[IO](cacheDir)
  implicit val cp: Checkpoint[IO] = CatsCheckpoint(memoize)

  for {
    trimmed  <- cp.checkpoint("trim",   IO(" hello "), identity[String], (s: String) => IO.pure(s.trim))
    length   <- cp.checkpoint("length", IO(trimmed),   identity[String], (s: String) => IO.pure(s.length))
    rendered <- cp.checkpoint("render", IO(length),    identity[Int],    (n: Int)    => IO.pure(s"len=$n"))
  } yield rendered
}

pipeline.unsafeRunSync()
```

## AI agent pipeline

LLM calls are slow and costly. Checkpoint each stage so that changing one stage only re-runs that stage.
Results survive restarts — you never pay twice for the same LLM call with the same input.

```scala
// pipeline.sc
//> using dep "io.github.mercurievv.aidclimbing::checkpoint-all:@AIDCLIMBING_VERSION@"
//> using dep "org.typelevel::log4cats-noop:2.6.0"

import cats.effect.{IO, IOApp}
import cats.mtl.Tell
import fs2.io.file.Path
import io.github.mercurievv.aidclimbing.checkpointall._
import io.github.mercurievv.aidclimbing.checkpoint.Checkpoint
import io.github.mercurievv.aidclimbing.checkpoint.ce.{CatsCheckpoint, LogTell}
import io.github.mercurievv.aidclimbing.checkpoint.file.FileMemoize
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

object AgentPipeline extends IOApp.Simple {
  implicit val logger: Logger[IO]       = NoOpLogger[IO]
  implicit val teller: Tell[IO, String] = LogTell[IO]

  def callLLM(prompt: String): IO[String] = IO(s"[LLM response to: $prompt]") // replace with real client

  def run: IO[Unit] = {
    val memoize = FileMemoize.string[IO](Path(".agent-cache"))
    implicit val cp: Checkpoint[IO] = CatsCheckpoint(memoize)

    val rawInput = "Summarize the quarterly report for ACME Corp"

    for {
      // Stage 1: cached after first run
      facts    <- cp.checkpoint("extract",   IO(rawInput), identity[String],
                    q => callLLM(s"Extract key facts: $q"))

      // Stage 2: cached after first run
      enriched <- cp.checkpoint("enrich",    IO(facts),    identity[String],
                    f => callLLM(s"Enrich with context: $f"))

      // Stage 3: iterate on this prompt freely — stages 1 & 2 stay cached
      summary  <- cp.checkpoint("summarize", IO(enriched), identity[String],
                    e => callLLM(s"Write a concise executive summary: $e"))

      _ <- IO.println(summary)
    } yield ()
  }
}
```

Run it:

```
scala-cli pipeline.sc
```

First run: all three LLM calls execute, results written to `.agent-cache/`.  
Second run (no input change): all three stages load from cache instantly.  
Iterate on the `"summarize"` prompt: only stage 3 re-runs; stages 1 & 2 replay from cache.
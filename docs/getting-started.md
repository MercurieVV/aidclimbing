# Getting Started

This example uses the aggregated module and demonstrates that repeated computations are cached behind a checkpoint key.

```scala mdoc
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.mtl.Tell
import io.github.mercurievv.aidclimbing.checkpointall._
import io.github.mercurievv.aidclimbing.checkpoint.ce.LogTell
import io.github.mercurievv.aidclimbing.checkpoint.Checkpoint
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

implicit val logger: Logger[IO] = NoOpLogger[IO]
implicit val teller: Tell[IO, String] = LogTell[IO]
```

Create an in-memory memoization backend:

```scala mdoc
val program = RefMemoize.make[IO].flatMap { memoize =>
  implicit val cp: Checkpoint[IO] = CatsCheckpoint(memoize)

  for {
    callCount <- Ref.of[IO, Int](0)
    expensive = (s: String) => callCount.update(_ + 1) *> IO.pure(s.length)
    first <- cp.checkpoint("step1", IO("hello"), identity[String], expensive)
    second <- cp.checkpoint("step1", IO("hello"), identity[String], expensive)
    count <- callCount.get
  } yield (first, second, count)
}
```

Run the program:

```scala mdoc
program.unsafeRunSync()
```

You can also compose multiple checkpointed stages in a pipeline:

```scala mdoc
val pipeline = RefMemoize.make[IO].flatMap { memoize =>
  implicit val cp: Checkpoint[IO] = CatsCheckpoint(memoize)

  for {
    trimmed <- cp.checkpoint("trim", IO(" hello "), identity[String], (s: String) => IO.pure(s.trim))
    length <- cp.checkpoint("length", IO(trimmed), identity[String], (s: String) => IO.pure(s.length))
    rendered <- cp.checkpoint("render", IO(length), identity[Int], (n: Int) => IO.pure(s"len=$n"))
  } yield rendered
}

pipeline.unsafeRunSync()
```

package io.github.mercurievv.aidclimbing

import cats.effect.{IO, Ref}
import cats.mtl.Tell
import cats.syntax.all._
import io.github.mercurievv.aidclimbing.checkpoint.Checkpoint
import io.github.mercurievv.aidclimbing.checkpoint.ce.{CatsCheckpoint, LogTell, RefMemoize}
import io.github.mercurievv.aidclimbing.checkpoint.syntax.CheckpointSyntax._
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

class CheckpointSpec extends CatsEffectSuite {

  test("checkpointed call computes once; same call without checkpoint always recomputes") {
    RefMemoize.make[IO].flatMap { memoize =>
      implicit val logger: Logger[IO] = NoOpLogger[IO]
      implicit val teller: Tell[IO, String] = LogTell[IO]
      implicit val cp = CatsCheckpoint(memoize)

      // The expensive computation we want to protect
      for {
        callCount <- Ref.of[IO, Int](0)
        expensiveCompute = (s: String) => callCount.update(_ + 1) *> IO.pure(s.length)

        // --- with checkpoint: key = the string itself ---
        // First call: cache miss → runs expensiveCompute
        r1 <- IO("hello").checkpoint("step1")(identity)(expensiveCompute)
        // Second call: cache hit → expensiveCompute NOT called
        r2 <- IO("hello").checkpoint("step1")(identity)(expensiveCompute)
        countAfterCheckpointed <- callCount.get

        // --- without checkpoint: same compute, same input ---
        // Each call runs expensiveCompute regardless
        r3 <- expensiveCompute("hello")
        r4 <- expensiveCompute("hello")
        countAfterPlain <- callCount.get

      } yield {
        // All four calls return the correct result
        assertEquals(r1, 5); assertEquals(r2, 5)
        assertEquals(r3, 5); assertEquals(r4, 5)

        // Checkpointed: 2 calls → 1 actual execution
        assertEquals(countAfterCheckpointed, 1)

        // Plain: 2 more calls → 2 more executions (total = 3)
        assertEquals(countAfterPlain, 3)
      }
    }
  }

  test("deleting memoized value in the middle of the pipe recomputes only that checkpoint") {
    RefMemoize.make[IO].flatMap { memoize =>
      implicit val logger: Logger[IO] = NoOpLogger[IO]
      implicit val teller: Tell[IO, String] = LogTell[IO]
      implicit val cp = CatsCheckpoint(memoize)

      for {
        step1Calls <- Ref.of[IO, Int](0)
        step2Calls <- Ref.of[IO, Int](0)
        step3Calls <- Ref.of[IO, Int](0)

        step1 = (s: String) => step1Calls.update(_ + 1) *> IO.pure(s.trim)
        step2 = (s: String) => step2Calls.update(_ + 1) *> IO.pure(s.length)
        step3 = (n: Int) => step3Calls.update(_ + 1) *> IO.pure(s"len=$n")

        pipe = (input: String) =>
          IO(input)
            .checkpoint("step1")(identity)(step1)
            .checkpoint("step2")(identity)(step2)
            .checkpoint("step3")(identity)(step3)

        firstRun <- pipe(" hello ")
        secondRun <- pipe(" hello ")
        countsAfterWarmup <- (step1Calls.get, step2Calls.get, step3Calls.get).tupled

        _ <- memoize.delete(Checkpoint.compositeKey("step2", "hello"))

        thirdRun <- pipe(" hello ")
        countsAfterDelete <- (step1Calls.get, step2Calls.get, step3Calls.get).tupled
      } yield {
        assertEquals(firstRun, "len=5")
        assertEquals(secondRun, "len=5")
        assertEquals(thirdRun, "len=5")

        assertEquals(countsAfterWarmup, (1, 1, 1))
        assertEquals(countsAfterDelete, (1, 2, 1))
      }
    }
  }
}

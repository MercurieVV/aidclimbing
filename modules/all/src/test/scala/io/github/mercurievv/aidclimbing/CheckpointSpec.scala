package io.github.mercurievv.aidclimbing

import cats.effect.{IO, Ref}
import io.github.mercurievv.aidclimbing.checkpoint.ce.{CatsCheckpoint, RefMemoize}
import io.github.mercurievv.aidclimbing.checkpoint.syntax.CheckpointSyntax._
import munit.CatsEffectSuite

class CheckpointSpec extends CatsEffectSuite {

  test("checkpointed call computes once; same call without checkpoint always recomputes") {
    RefMemoize.make[IO].flatMap { memoize =>
      implicit val cp = CatsCheckpoint(memoize)

      // The expensive computation we want to protect
      for {
        callCount <- Ref.of[IO, Int](0)
        expensiveCompute = (s: String) => callCount.update(_ + 1) *> IO.pure(s.length)

        // --- with checkpoint: key = the string itself ---
        // First call: cache miss → runs expensiveCompute
        r1 <- IO("hello").checkpoint(identity, expensiveCompute)
        // Second call: cache hit → expensiveCompute NOT called
        r2 <- IO("hello").checkpoint(identity, expensiveCompute)
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
}
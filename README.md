# aidclimbing

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mercurievv.aidclimbing/checkpoint-all_2.13)](https://search.maven.org/search?q=g:io.github.mercurievv.aidclimbing)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Scala](https://img.shields.io/badge/scala-2.13%20%7C%203-red.svg)](https://scala-lang.org)

Checkpointing library for exploring Scala pipelines, streams, and data flows — including AI agent workflows.

Named checkpoints enforce a simple contract: **same input → same output, skip recomputation**. Expensive stages (LLM calls, slow transforms, external API calls) are cached by key; only stages whose input changes re-execute.

Designed for iterative research with `scala-cli` as a lightweight, reproducible alternative to notebooks.

```scala
// Scala 3 — same input key → cached result, compute skipped
val memoize = FileMemoize.string[IO](Path(".cache"))
given Checkpoint[IO] = CatsCheckpoint(memoize)
for
  r1 <- IO("hello").checkpoint("step")(identity)(s => IO(s.toUpperCase))
  r2 <- IO("hello").checkpoint("step")(identity)(s => IO(s.toUpperCase))
  // compute ran once; r1 == r2 == "HELLO"
yield (r1, r2)
```

**[Full documentation and examples →](https://mercurievv.github.io/aidclimbing/)**

## Quick install

**SBT:**
```scala
libraryDependencies += "io.github.mercurievv.aidclimbing" %% "checkpoint-all" % "<version>"
```

**scala-cli:**
```scala
//> using dep "io.github.mercurievv.aidclimbing::checkpoint-all:<version>"
```
# aidclimbing

Checkpointing library for exploring Scala pipelines, streams, and data flows.

## What it does

`aidclimbing` adds named checkpoints to your processing pipeline. A checkpoint has an ID and an input key: if you run the same stage again with the same input, the cached result is returned instead of recomputing. This is the FP purity contract applied at the pipeline-stage level — **same input, same output, skip the work**.

```
Input → [checkpoint "parse"] → [checkpoint "enrich"] → [checkpoint "render"] → Output
               ↓                         ↓                        ↓
        cache miss: run          cache hit: skip          cache miss: run
```

Think of it as a lightweight alternative to notebooks for iterative pipeline research with `scala-cli`. Build a pipeline as ordinary Scala code, annotate expensive stages with checkpoints, and iterate freely — only changed stages re-execute.

## AI agentic workflows

LLM calls are slow and expensive. When you're iterating on one stage of an AI agent pipeline, you don't want to re-run upstream LLM and tool calls every time. `aidclimbing` checkpoints each stage: as long as the input to a stage hasn't changed, the cached result is replayed instantly.

```
prompt → [checkpoint "llm-extract"] → [checkpoint "tool-call"] → [checkpoint "summarize"] → output
                  ↓                             ↓                           ↓
           cached (no change)           cached (no change)           cache miss: re-run
```

Change the summarization prompt → only the last stage re-executes. File-backed persistence means cached results survive restarts, so you never pay twice for the same LLM call.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `checkpoint-core` | `checkpoint-core` | `Checkpoint` and `Memoize` traits |
| `checkpoint-ce` | `checkpoint-ce` | Cats Effect + Log4Cats integration |
| `checkpoint-file` | `checkpoint-file` | FS2-backed file persistence |
| `checkpoint-all` | `checkpoint-all` | Convenience re-export of all modules |

## Installation

**SBT:**
```scala
libraryDependencies += "io.github.mercurievv.aidclimbing" %% "checkpoint-all" % "@AIDCLIMBING_VERSION@"
```

**scala-cli:**
```scala
//> using dep "io.github.mercurievv.aidclimbing::checkpoint-all:@AIDCLIMBING_VERSION@"
```

## How checkpoints work

```
cp.checkpoint(id, inputEffect, keyFn, compute)
               │       │          │       └── expensive computation: A => F[V]
               │       │          └────────── extract cache key from input: A => K
               │       └───────────────────── effect producing input: F[A]
               └───────────────────────────── stable name for this stage
```

Cache key = `"$id::${keyFn(input).show}"`. Same key → cached result returned, `compute` skipped.

See the [Getting Started](getting-started.md) guide for full examples including `scala-cli` usage.
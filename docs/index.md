# Aidclimbing

`aidclimbing` provides reusable checkpointing abstractions for Scala pipelines.

## Modules

- `checkpoint-core`: base `Checkpoint` and `Memoize` abstractions
- `checkpoint-ce`: Cats Effect integrations
- `checkpoint-file`: FS2-backed file persistence
- `checkpoint-all`: convenience import that re-exports the full surface area

## Installation

```scala
libraryDependencies += "io.github.mercurievv.aidclimbing" %% "checkpoint-all" % "@AIDCLIMBING_VERSION@"
```

## Documentation

See the getting started guide in this site navigation.

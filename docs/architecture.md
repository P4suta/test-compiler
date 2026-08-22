# Architecture

## Pipeline

1. The Gradle plugin resolves compiled production/test roots and the test runtime classpath without changing the target source tree.
2. A supervised Java 21 worker runs the unmodified baseline through JUnit Platform and the optional TestNG adapter. A Java Agent instruments each application/test class once and emits a versioned JSONL observation stream.
3. The engine builds a test→production impact catalog from returns, semantic calls, and global-state accesses. Unsupported graphs remain diagnostics.
4. Each candidate is activated by a runtime plan ID in a fresh worker. Surviving behavior/effect plans and failing reversed-order plans are executed a second time.
5. Only matching outcomes and normalized traces become proven findings. The stable model writes JSON first, then projects SARIF. IntelliJ reads that same JSON.

## Module boundaries

- `model`: stable Java-visible report types, schema, and finding IDs.
- `agent`: Java-only bootstrap/runtime and ASM boundary. Kotlin metadata, source files, JSR-45 SMAP, and line tables are preserved/read without compiler internals.
- `engine`: observation IR, challengers, deterministic witness generation, cache key, replay, and process-tree supervision.
- `gradle-plugin`: configuration precedence and fixed Gradle task surface; embeds the engine and agent runtime jars.
- `cli`: thin `testc` command surface over the project's Wrapper.
- `intellij-plugin`: report projection and background Gradle Tooling API actions; no engine copy.

## Trust boundary

Tests execute with their normal project permissions. The agent records types, semantic labels, normalized test names, and hashes—not environment/property values or arbitrary object `toString()` output. Network/native observations disable cache reuse. Reports and run evidence remain under `build/reports/test-compiler`; the optional reusable cache is under the OS user cache.

## Stable identity

Finding IDs hash `kind + normalized source path + line + symbol + target + operator` with a versioned canonical encoding. Full inputs remain in the report plan. A theoretical truncated-hash collision is sorted by full plan and receives a deterministic numeric suffix.

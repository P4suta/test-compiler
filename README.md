# test-compiler

`test-compiler` compiles a passing Gradle JVM test suite into deterministic counterfactual diagnostics. It does not replace JUnit, TestNG, Kotest, Spock, or Gradle `Test`; it asks whether the current tests distinguish a conservative implementation change, observe a semantic side effect, or survive only in one shared-state order.

The engine makes no outbound network calls and provides no telemetry, AI diagnosis,
hosted dashboard, or automatic patch generation. Gradle dependency resolution and
the target test suite may still use the network. Production and test sources are
never rewritten.

> **Development status:** `0.1.0` is not published yet. Build from this repository
> for evaluation; the coordinates below describe the planned first release.

## What v0.1 proves

- `behavior-gap`: activates Boolean, numeric, string, and nullable return challengers at runtime after one ASM instrumentation pass. The report includes a deterministic boundary witness; supported shapes are primitives/boxed values, strings, enums, nullable values, arrays/collections, Java records, and Kotlin data classes.
- `effect-gap`: suppresses Spring application events, JDBC writes/commits, messaging sends, or an explicitly configured semantic effect. Logging and metrics are excluded.
- `order-dependency`: correlates reads/writes of system properties, locale/timezone, and files, then replays the smallest writer/reader pair in reversed order.

A result is `PROVEN` only when the same saved plan is executed again with the same outcome and normalized trace. Timeouts, unstable results, unsupported object graphs, and baseline failures remain explicit and do not count as gated findings.

## Apply it

Java 21 bytecode is the minimum target.

```kotlin
plugins {
    id("io.github.p4suta.test-compiler") version "0.1.0"
}

testCompiler {
    // The default is diagnostic-only.
    gate.set("new") // or "all"
}
```

Run `./gradlew testCompilerCheck`. The authoritative artifacts are:

- `build/reports/test-compiler/run-report-v1.json`
- `build/reports/test-compiler/run-report-v1.schema.json`
- `build/reports/test-compiler/test-compiler.sarif`

SARIF and the IntelliJ UI are projections of `run-report-v1.json`; neither has an independent finding model.

## CLI

The `cli` application installs as `testc` and deliberately delegates execution to the repository's Gradle Wrapper:

```text
testc check
testc check --changed=origin/main
testc replay tc-0123456789abcdef0123
testc doctor
testc init
testc baseline update
```

Equivalent Gradle tasks are `testCompilerCheck`, `testCompilerReplay`, `testCompilerDoctor`, and `testCompilerBaseline`.

`testc init` never overwrites `.test-compiler.toml`. `baseline update` is the only command that writes `.test-compiler-baseline.json`.

## Configuration

Precedence is built-in defaults → `.test-compiler.toml` → Gradle extension → CLI/Gradle properties. A minimal file is:

```toml
gate = "diagnostic-only" # diagnostic-only, new, all
timeout-seconds = 300
max-challenges = 64
tracked-environment = ["CI"]
tracked-files = ["gradle/libs.versions.toml"]
custom-effects = ["com.example.Outbox#append"]
```

Environment values are hashed into cache keys and are never placed in reports. Observed application values are represented by types and synthetic witnesses; string values, property values, paths, and secrets are not persisted.

## Determinism and cache

The cache key covers JDK, configuration, source/class files, the complete runtime classpath, explicitly tracked files, and hashes of tracked environment inputs. Any observed network or native access makes that run non-cacheable. Broken cache entries are ignored with a diagnostic. `--changed=<ref>` checks impacted source and merges unaffected entries only from the previous complete catalog, so the report continues to describe the full catalog.

## Framework and IDE adapters

JUnit Platform discovery supports Jupiter, Vintage, Kotest, and Spock when their engines are on the test runtime classpath. A reflection-isolated TestNG adapter is activated only when TestNG is present. Spring labels and effect interception activate only when matching Spring APIs appear in application bytecode.

The IntelliJ plugin targets 2025.3 through 2026.2. It reads the authoritative report for Problems and gutter markers, shows findings in a tool window, and invokes check/replay/baseline through the Gradle Tooling API on a cancellable background task; the engine never runs on the EDT.

## Build

The repository pins the JDK in `mise.toml` and Gradle in the Wrapper:

```shell
mise install
./gradlew check
./gradlew :gradle-plugin:test
./gradlew :intellij-plugin:buildPlugin
```

The mixed Kotlin-production/Java-test TestKit fixture is an end-to-end contract: it verifies all three finding kinds, stable JSON/SARIF IDs, source positions, replay metadata, and the `0` boundary witness.

See [Architecture](docs/architecture.md), [report schema](model/src/main/resources/io/github/p4suta/testcompiler/model/run-report-v1.schema.json), [support](SUPPORT.md), and [security policy](SECURITY.md).

## License

Licensed under either MIT or Apache-2.0, at your option.

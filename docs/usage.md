# Usage guide

`test-compiler` compiles a passing Gradle test suite into deterministic counterfactual diagnostics.
Use it with JUnit, TestNG, Kotest, Spock, or Gradle `Test`.
It checks whether tests distinguish an implementation change, observe a semantic effect, or depend on one shared-state order.

The engine makes no outbound network calls and provides no telemetry.
Gradle dependency resolution and the target test suite may use the network.
Production and test sources stay unchanged.

## Source evaluation

The first `0.1.0` release is unpublished.
Build from this repository for evaluation.
Java 21 bytecode is the minimum target.

```sh
mise install
mise exec -- sh ./gradlew :cli:installDist
mise exec -- ./cli/build/install/testc/bin/testc --help
```

On Windows, use `gradlew.bat` and `cli\build\install\testc\bin\testc.bat`.
See [Contributing](../CONTRIBUTING.md) for the complete project checks.

## Findings

- `behavior-gap` activates Boolean, numeric, string, and nullable return challengers after one instrumentation pass.
  Supported shapes include primitives, boxed values, strings, enums, nullable values, arrays, collections, Java records, and Kotlin data classes.
  The report includes a deterministic boundary witness.
- `effect-gap` suppresses Spring events, database writes or commits, messaging sends, or an explicitly configured semantic effect.
  Logging and metrics remain outside this category.
- `order-dependency` tracks reads and writes of system properties, locale, timezone, and files, then replays a writer and reader in reversed order.

A finding is `PROVEN` only when replaying its saved plan produces the same outcome and normalized trace.
Timeouts, unstable results, unsupported object graphs, and baseline failures remain diagnostics.

## Gradle integration

The following snippet describes the planned plugin release:

```kotlin
plugins {
    id("io.github.p4suta.test-compiler") version "0.1.0"
}

testCompiler {
    gate.set("new")
}
```

The default gate is diagnostic only.
The other modes are `new` and `all`.
Run `./gradlew testCompilerCheck` after applying the plugin.
The authoritative outputs are:

- `build/reports/test-compiler/run-report-v1.json`
- `build/reports/test-compiler/run-report-v1.schema.json`
- `build/reports/test-compiler/test-compiler.sarif`

The source analysis output and IntelliJ UI project the same report model.
See the [report schema](../model/src/main/resources/io/github/p4suta/testcompiler/model/run-report-v1.schema.json).

## CLI

The `testc` program delegates checks to the target project's Gradle wrapper.

```text
testc check
testc check --changed=origin/main
testc replay tc-0123456789abcdef0123
testc doctor
testc init
testc baseline update
```

Equivalent Gradle tasks are `testCompilerCheck`, `testCompilerReplay`, `testCompilerDoctor`, and `testCompilerBaseline`.
`testc init` keeps an existing `.test-compiler.toml`.
`baseline update` is the only command that writes `.test-compiler-baseline.json`.

## Configuration

Precedence is built-in defaults, `.test-compiler.toml`, the Gradle extension, then command-line properties.
A minimal configuration is:

```toml
gate = "diagnostic-only"
timeout-seconds = 300
max-challenges = 64
tracked-environment = ["CI"]
tracked-files = ["gradle/libs.versions.toml"]
custom-effects = ["com.example.Outbox#append"]
```

Environment values enter cache keys as hashes and stay out of reports.
Observed values use types and synthetic witnesses.
Reports exclude raw string values, property values, paths, and secrets.

## Determinism and cache

Cache keys cover the runtime, configuration, source and class files, complete runtime classpath, tracked files, and hashes of tracked environment inputs.
Observed network or native access disables reuse for that run.
Invalid cache entries produce a diagnostic.
`--changed=<ref>` checks affected sources and uses the previous complete catalog for unaffected entries.
The resulting report still describes the complete catalog.

## Framework and IDE adapters

JUnit Platform discovery supports Jupiter, Vintage, Kotest, and Spock when their engines appear on the test runtime classpath.
A separate TestNG adapter activates when TestNG is present.
Spring labels and effect interception activate when matching Spring APIs appear in app bytecode.

The IntelliJ plugin targets 2025.3 through 2026.2.
It displays the report in Problems, gutter markers, and a tool window.
Check, replay, and baseline operations use the Gradle Tooling API on a cancellable background task.
The engine stays off the UI thread.

The mixed Kotlin and Java test fixture checks all three finding kinds, stable report identifiers, source positions, replay metadata, and the `0` boundary witness.
See [Architecture](architecture.md), [Support](../SUPPORT.md), and [Security](../SECURITY.md).

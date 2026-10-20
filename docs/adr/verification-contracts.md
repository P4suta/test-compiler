---
date: 2026-10-21
---

# Verification contracts

## Status

Accepted.

## Context

Instrumentation and worker processes need Java runtime and host compatibility checks.
Gradle and IntelliJ integrations have separate compatibility boundaries.
Repeating all checks for documentation changes adds no evidence for those boundaries.

## Decision

Select checks from the [affected source paths](../../.github/filters/ci.yml).
Keep workflow validation and the required status on every change.
Run the complete model and core suite on the minimum Java version on Linux.
Keep instrumentation and worker tests for every declared runtime and host pair, using the core job for the minimum Linux pair.
Use Temurin locally and in hosted jobs so the Java setup action and dependency tracking share a supported distribution.
Pin the local Java release while retaining the declared compatibility versions.

Test the current Gradle wrapper in the core job and the native host lanes.
Keep the oldest supported Gradle and the existing Gradle 8 compatibility baseline in separate adapter lanes.
Run IDE compatibility checks when the report model, Gradle adapter, IDE code, or shared build inputs change.
Keep Java and Kotlin security analysis for source, build, and analyzer changes.
Remove duplicate main runs and scheduled analysis.
A manual CI request selects every CI lane.

Use the Gradle setup action's built-in wrapper validation.
Release jobs start without a restored Gradle user-home cache.
Publication keeps its existing manual confirmation and environment gate.

## Consequences

Documentation changes need no compiler or IDE downloads.
Runtime and integration changes keep their distinct compatibility evidence.
Pure model tests run once, and default-wrapper adapter tests cover each native host once.
The required check rejects failed selection and failed selected jobs.
Unconditional full builds and removing native compatibility coverage are rejected.

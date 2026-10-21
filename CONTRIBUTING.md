# Contributing

See the [usage guide](docs/usage.md) for source evaluation, configuration, and report contracts.
See [verification contracts](docs/adr/verification-contracts.md) for the checks each change selects.

Use the repository wrapper and Java runtime pinned by `mise.toml`.
Before submitting a change, run:

```shell
./gradlew check
./gradlew :gradle-plugin:test
./gradlew :intellij-plugin:verifyPluginProjectConfiguration
```

Changes to the authoritative report require a schema update and contract tests.
New challengers must use the existing `ChallengePlan`/observation/witness pipeline and prove reproducibility before publishing a finding.

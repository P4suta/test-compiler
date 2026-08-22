# Contributing

Use the repository Wrapper and the JDK pinned by `mise.toml`. Before submitting a change, run:

```shell
./gradlew check
./gradlew :gradle-plugin:test
./gradlew :intellij-plugin:verifyPluginProjectConfiguration
```

Changes to the authoritative report require a schema update and contract tests. New challengers must use the existing `ChallengePlan`/observation/witness pipeline and prove reproducibility before publishing a finding.

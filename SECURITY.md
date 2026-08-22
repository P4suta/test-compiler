# Security policy

Report security issues privately through GitHub Security Advisories for `p4suta/test-compiler`.

`test-compiler` performs no telemetry or tool-initiated network communication. Target tests may access external resources; observed network/native access disables result caching but does not sandbox the target process. Run only trusted builds and tests.

Reports do not store environment values, system-property values, arbitrary object rendering, or raw file paths observed at runtime. Synthetic witness values are explicitly marked `synthetic`.

# Module: Check Maven

Verify that Maven is installed and meets the minimum version requirement.

## Preconditions

Run this check only when the source project's **primary** build tool is Maven.
**Tiebreaker:** if both `pom.xml` and `build.gradle(.kts)` exist, `build.gradle(.kts)` wins — treat as Gradle and **SKIP** this module.

## Decision Table

| Condition | Gate Result |
|---|---|
| `pom.xml` present (and no `build.gradle(.kts)`) and `mvn -version` >= 3.9 | **PASS** — continue |
| `pom.xml` present (and no `build.gradle(.kts)`) and `mvn -version` < 3.9 | **FAIL** — stop, warn user |
| `pom.xml` present (and no `build.gradle(.kts)`) and `mvn` not found on PATH | **FAIL** — stop, warn user |
| `build.gradle(.kts)` present (with or without `pom.xml`) | **SKIP** — Gradle project |
| Neither `pom.xml` nor `build.gradle(.kts)` found | **FAIL** — unknown build tool, stop and ask user |

## Instructions

- [ ] Check for `build.gradle` or `build.gradle.kts` first. If found — **SKIP**. Log: `maven-check: SKIPPED — Gradle project`.
- [ ] Check for `pom.xml`. If not found — **FAIL**. Warn: "No recognised build file found (`pom.xml` or `build.gradle`). Cannot determine build tool." Then stop.
- [ ] Run `mvn -version` and capture the output.
- [ ] Parse the Maven version number from the output (e.g. `Apache Maven 3.9.6`).
- [ ] If version **>= 3.9** — log `maven-check: PASS (x.y.z)` and proceed.
- [ ] If version **< 3.9** or `mvn` is not found — this is a toolchain blocker:
    - **interactive** — warn the user: "Maven 3.9 or later is required. Currently installed: <detected version or 'none'>. Please install Maven 3.9+ and ensure it is on your PATH before retrying." Then **stop the migration**.
    - **autonomous** — emit a structured blocking failure recorded under `unresolved_issues:` (severity ERROR, phase `maven-check`, message as above), then **stop the migration**.

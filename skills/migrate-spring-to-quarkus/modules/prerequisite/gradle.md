# Module: Check Gradle

Verify that Gradle is installed and meets the minimum version requirement.

## Preconditions

Run this check only when the source project's **primary** build tool is Gradle.
**Tiebreaker:** if both `pom.xml` and `build.gradle(.kts)` exist, `build.gradle(.kts)` wins — treat as Gradle and **run** this module (maven.md will self-skip in that case).

## Decision Table

| Condition | Gate Result |
|---|---|
| `build.gradle(.kts)` present and `./gradlew -version` >= 8 | **PASS** — continue (wrapper preferred) |
| `build.gradle(.kts)` present and `gradle -version` >= 8 | **PASS** — continue (system Gradle fallback) |
| `build.gradle(.kts)` present but Gradle version < 8 | **FAIL** — stop, warn user |
| `build.gradle(.kts)` present but neither wrapper nor `gradle` found | **FAIL** — stop, warn user |
| Only `pom.xml` present (no `build.gradle(.kts)`) | **SKIP** — Maven project |

## Instructions

- [ ] Check for `build.gradle` or `build.gradle.kts` in the source project root.
- [ ] If neither found — **SKIP** this module (Maven project). Log: `gradle-check: SKIPPED — Maven project`.
- [ ] Prefer the Gradle wrapper: run `./gradlew -version` from the project root.
      If the wrapper is not present, fall back to `gradle -version`.
- [ ] Parse the Gradle version number from the output (e.g. `Gradle 8.5`).
- [ ] If version **>= 8** — log `gradle-check: PASS (x.y)` and proceed.
- [ ] If version **< 8** or Gradle is not found — this is a toolchain blocker:
    - **interactive** — warn the user: "Gradle 8 or later is required. Currently installed: <detected version or 'none'>. Please install Gradle 8+ (or use the project Gradle wrapper) and ensure it is on your PATH before retrying." Then **stop the migration**.
    - **autonomous** — emit a structured blocking failure recorded under `unresolved_issues:` (severity ERROR, phase `gradle-check`, message as above), then **stop the migration**.

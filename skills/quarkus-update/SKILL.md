---
name: quarkus-update
description: Use when working in a Quarkus project and the user wants to check if their build files are up-to-date, compare project structure against a reference, or upgrade their Quarkus version. Triggers on "check project", "update quarkus", "is my project up to date", "compare build", "quarkus upgrade".
---

# Quarkus Project Update

Check if a Quarkus project needs upgrading, analyse what changes are required, and — with user confirmation — apply them. If the user explicitly asks to apply the update without confirmation, skip the confirmation in Step 4 and proceed directly to Step 5.

## Step 1: Detect Build Tool and Version

Identify which build file exists in the project root:

| File | Build Tool | Tag prefix |
|------|-----------|------------|
| `pom.xml` | Maven | `maven-` |
| `build.gradle` | Gradle | `gradle-` |
| `build.gradle.kts` | Gradle Kotlin DSL | `gradle-kotlin-dsl-` |

Extract the current Quarkus version:

- **Maven (`pom.xml`):** Look for the `<quarkus.platform.version>` property, or the version of `quarkus-bom` in `<dependencyManagement>`
- **Gradle (`build.gradle`):** Look for `quarkusPlatformVersion` in the `ext` block or `gradle.properties`
- **Gradle KTS (`build.gradle.kts`):** Look for `val quarkusPlatformVersion` or the BOM declaration

## Step 2: Run Update Dry-Run

Run the dry-run using the first available tool in priority order: Quarkus CLI → build tool wrapper → system build tool.

| Tool | Command |
|------|---------|
| Quarkus CLI | `quarkus update --dry-run` |
| Maven wrapper | `./mvnw quarkus:update -DrewriteDryRun` |
| Maven | `mvn quarkus:update -DrewriteDryRun` |
| Gradle wrapper | `./gradlew quarkusUpdate --rewriteDryRun` |
| Gradle | `gradle quarkusUpdate --rewriteDryRun` |

This produces (without modifying any files):
- **Console output:** the target Quarkus version, BOM update suggestions, extension sync status, matched migration recipes
- **`{output_dir}/rewrite/rewrite.patch`** — the actual diff of changes the update would apply
- **`{output_dir}/rewrite/rewrite.yaml`** — the full OpenRewrite recipe (version bumps + migration rules)
- **`{output_dir}/rewrite/rewrite.log`** — detailed execution log

Where `{output_dir}` is `target` for Maven and `build` for Gradle.

If `{output_dir}/rewrite/rewrite.patch` exists, read it to understand what code-level changes the update covers. If it does not exist, the update only involves a version bump with no code-level migrations. Extract the target Quarkus version from the console output.

If the dry-run reports nothing to update, inform the user and stop here.

## Step 3: Fetch Generator Diff

Construct the current and target tags from the detected versions:

`{tag_prefix}{version}` — e.g., `maven-3.15.7`, `maven-3.36.1`

Get the structural diff between the current and target versions using the GitHub API:

```
https://api.github.com/repos/quarkusio/code-with-quarkus-compare/compare/{current_tag}...{target_tag}
```

For example: `https://api.github.com/repos/quarkusio/code-with-quarkus-compare/compare/maven-3.15.7...maven-3.36.1`

The response is JSON. Each entry in the `files` array has a `filename` and a `patch` field containing the unified diff for that file. Check the top-level `truncated` field — if `true`, the API response is incomplete; note this in the report and direct the user to browse the full diff at `https://github.com/quarkusio/code-with-quarkus-compare/compare/{current_tag}...{target_tag}`.

If the API call fails (e.g., 404 or 422 when a tag does not exist), note that structural comparison is unavailable for that version and continue without it.

This reveals structural changes that `quarkus update` may not cover, such as:
- New plugin configurations (e.g., `<argLine>` additions)
- Surefire/failsafe version bumps
- Wrapper script updates
- Dockerfile changes
- New or removed boilerplate files

## Step 4: Present Analysis and Ask for Confirmation

Present a combined report:

1. **Current status:** Build tool, current Quarkus version, target version
2. **What `quarkus update` will handle:** Summarize the patch (version bumps, dependency renames, config key migrations)
3. **What `quarkus update` does NOT cover:** For each changed file/section in the generator diff, check whether the rewrite patch already covers it. Only list the differences that are absent from the patch, with an explanation of why each one matters
4. **Full comparison link:** `https://github.com/quarkusio/code-with-quarkus-compare/compare/{current_tag}...{target_tag}` (for the user to browse)

Ask the user whether to proceed with applying the changes. **Stop here and wait for the user's response before continuing to Step 5.**

## Step 5: Apply Changes (if confirmed)

Run the actual update using the first available tool in priority order: Quarkus CLI → build tool wrapper → system build tool.

| Tool | Command |
|------|---------|
| Quarkus CLI | `quarkus update` |
| Maven wrapper | `./mvnw quarkus:update` |
| Maven | `mvn quarkus:update` |
| Gradle wrapper | `./gradlew quarkusUpdate` |
| Gradle | `gradle quarkusUpdate` |

Then apply the remaining structural changes identified in Step 4 that `quarkus update` does not cover. For each change, check whether it is relevant to the user's project before applying — do not blindly copy everything from the reference. Make each applied change explicit.

Finally, verify the build compiles and tests pass:

| Tool | Command |
|------|---------|
| Maven wrapper | `./mvnw verify` |
| Maven | `mvn verify` |
| Gradle wrapper | `./gradlew build` |
| Gradle | `gradle build` |

Report the outcome. If the build fails, diagnose the failure before declaring the upgrade complete.

# Two-directory migration model (source -> target)

- Status: proposed
- Date: 2026-08-28

## Context and Problem Statement

The `migrate-spring-to-quarkus` skill currently migrates projects in place: the agent transforms the source project inside its own directory, 
and the migration result replaces the original code.

As the skill evolves to support features like comparison-based validation (proposed in issue #39) and resumable state (issue #40), 
an alternative model has surfaced: 
migrating into a separate target directory, keeping the source project untouched.

## Decision Drivers

- **Comparison-based verification.** Verifying migration correctness by comparing metadata extracted from the source code against metadata extracted
from the target code (entities, REST endpoints, messaging channels, configuration) requires both versions of the code to exist at the same time. 
This applies to any tool or check doing such a comparison; the validators proposed in issue #39 are one candidate, still under discussion.
- **Cleaner reasoning for agents.** With a read-only source, the agent cannot accidentally destroy unmigrated code, 
and "what is left to migrate" is always answerable by comparing the two trees. 
The target project follows the target framework conventions from scratch instead of inheriting the legacy layout.
- **Auditability.** The comparison between source and target is a complete, permanent record of the migration.

## Considered Options

### In-place migration (status quo)

The agent transforms the source project inside its own directory. 
Simpler to explain, works naturally with the existing git workflow (branch per run on the same repo), and does not duplicate disk usage.

However, once a module runs the original code is gone, preventing comparison-based validation. 
The agent can accidentally destroy unmigrated code, and there is no permanent record of the changes.

### Two-directory migration (source -> target)

The agent reads from a read-only source directory and writes the migrated project 
into a separate target directory. Both code sources coexist, enabling comparison-based verification, safer agent reasoning, and a complete audit trail.

Trade-off: disk usage doubles per migration run (acceptable for the project sizes targeted), and `SKILL.md` plus modules must thread two paths instead of one.

## Decision

The skill adopts a two-directory migration model:

- **Source directory**: the original project to migrate, read-only. The skill may write extraction metadata under `<source>/migration-metadata/` to describe the source app. These files are reusable across migration runs against the same project, so they live with it. Nothing is ever written at the source root or anywhere else in the source tree.
- **Target directory**: the generated migrated project. All migration artifacts other than the source-side extractions live here. By default, it is named `<source-name>-quarkus` as a sibling of the source. In interactive mode the skill proposes this default and asks for confirmation; in autonomous mode it uses it directly.


## Scenarios

### Changes to the test sub-project (`./tests`)

Today the harness copies `tests/projects/<name>/source/` into a workdir and the agent migrates it in place; all checks run against that single directory. 
With this decision:

- The specific runner creates a separate target directory per run (e.g. `target/workdirs/<name>/` as read-only source copy and `target/workdirs/<name>-quarkus/` as migration target) and pass both paths in the prompt.
- All automated checks (build, tests pass, no Spring deps, has Quarkus, starts up, smoke tests) point to the target directory.
- Each project under `tests/projects/` keeps, next to `source/`, a **reference migrated project** (e.g. `migrated/`). This reference allows:
  - a user to verify and compare a migration run against a known-good result,
  - documenting the migration process step by step,
  - a CI job to compile and test both the source and the migrated reference,
  - supporting several source versions over time (Spring Boot 3.x, 4.x, ...) with their corresponding references.

## Consequences

Positives:

- Comparison-based verification becomes possible: any tool can compare source and target extractions (the validators proposed in issue #39 are one candidate, if adopted).
- Migrating the same source repeatedly (benchmark runs) can reuse the source-side extractions without re-running them.
- The comparison between source and target is a permanent audit trail.

Negatives:

- `SKILL.md` and the modules must resolve and thread two paths instead of assuming the working directory is the project.
- The git workflow module must be redesigned: the target is a new project, so the branch-per-run model on the original repo no longer applies as-is.
- Documentation (skill README, repo README) must describe the two-directory usage.
- Disk usage doubles per migration run.

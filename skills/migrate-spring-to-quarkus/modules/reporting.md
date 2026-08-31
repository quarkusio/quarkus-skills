---
name: reporting-agent
description: Final Reporting Agent. Generates comprehensive migration summary (migration-summary.md).
  Aggregates all phase reports, documents manual review items, and provides next steps.
license: Apache-2.0
metadata:
  phase: final
  agent_type: reporting
---

# Final — Reporting Agent

## Purpose

Generate comprehensive migration summary report.

## Inputs

- All phase reports from `migration-reports/` directory
- `migration-spec.yaml` (root level)
- `migration-metadata/migration-context.json`

## Steps

1. Read all phase reports
2. Aggregate statistics
3. Identify manual review items
4. **Read `execution.mode`, `migration_strategy`, `decisions:`, `skip:`, and `unresolved_issues:` from `migration-spec.yaml`**
5. Calculate migration metrics
6. Generate migration-summary.md

## Reporting execution mode, decisions, and unresolved issues

The summary MUST record how the migration was run and what remains open — this is the primary place an
**autonomous** run surfaces problems it documented rather than paused on:

- **Execution mode & strategy** — state `execution.mode` (interactive/autonomous) and the resolved
  `migration_strategy.migration_mode` (the full-vs-compat strategy).
- **Decisions** — list the entries from the spec's `decisions:` section. In autonomous mode these are the
  agent-selected technology choices and their one-line rationales; include them so the reader knows what was
  chosen on their behalf.
- **Unresolved major issues** — render every entry from `unresolved_issues:` in a dedicated section
  (phase, issue, files, attempted fixes, severity). If the list is non-empty, the migration **Status** must
  reflect it (e.g. ⚠️ COMPLETED WITH UNRESOLVED ISSUES) rather than ✅ COMPLETED.
- **Skipped features** — list the entries from `skip:`.

If `unresolved_issues:` is empty, print "None" under that section (same convention as Manual Review Items).

## Output

**File Location:** `migration-summary.md`

This summary report should be created in the target Quarkus project at `<quarkus_target_dir>/migration-summary.md` (root level).

The report should aggregate information from:
- `migration-reports/phase-03-project-bootstrap.json`
- `migration-reports/phase-04-database-migration.json`
- `migration-reports/phase-05-persistence-migration.json`
- `migration-reports/phase-06-service-migration.json`
- `migration-reports/phase-07-messaging-migration.json`
- `migration-reports/phase-08-web-migration.json`
- `migration-reports/phase-08b-web-views-migration.json` (if view layer migration was performed)
- `migration-reports/phase-09-configuration-migration.json`
- `migration-reports/phase-11-validation.json`
- `migration-metadata/migration-context.json`

Example:

```markdown
# Spring Boot to Quarkus Migration Summary

## Overview
- **Project**: myapp-quarkus
- **Migration Date**: 2024-01-15
- **Execution Mode**: autonomous
- **Strategy**: full-migration
- **Status**: ⚠️ COMPLETED WITH UNRESOLVED ISSUES

## Statistics
- **Files Modified**: 87
- **Files Created**: 24
- **Controllers Migrated**: 8
- **Services Migrated**: 15
- **Repositories Migrated**: 12
- **Entities Migrated**: 42
- **Messaging Listeners**: 5

## Build Status
- **Compilation**: ✅ PASS
- **Tests**: ✅ PASS
- **Package**: ✅ PASS

## Performance Improvements
- **Startup Time**: 2.5s → 0.8s (68% faster)
- **Memory Usage**: 512MB → 256MB (50% reduction)
- **Build Time**: 45s → 12s (73% faster)

## Decisions
- **Persistence**: Hibernate ORM with Panache — least boilerplate, JPA detected (agent-selected)
- **REST framework**: Quarkus REST (RESTEasy Reactive) — recommended default (agent-selected)
- **View technology**: Qute — 3 JSF files (< 5 threshold) (agent-selected)

## Manual Review Items
- None

## Unresolved Issues
- **Phase 5 (persistence)**: `OrderRepository.findByStatus` could not be migrated to Panache — 3 fix
  attempts exhausted. Files: `src/main/java/com/example/OrderRepository.java`. Severity: ERROR

## Skipped Features
- None

## Next Steps
1. Resolve the items under **Unresolved Issues** above
2. Run full test suite
3. Performance testing
4. Deploy to staging
5. Update documentation
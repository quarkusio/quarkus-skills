---
name: service-migration-agent
description: Phase 6 Service Layer Migration Agent. Entry point — reads migration-spec.yaml and routes
  to the compat or full-migration sub-agent based on migration_strategy.service_layer.
license: Apache-2.0
metadata:
  phase: 6
  agent_type: migration
---

# Phase 6 — Service Layer Migration Agent

## Purpose

Convert Spring service layer components (`@Service`, `@Component`) to Quarkus CDI beans.

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-06-service-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- `migration-spec.yaml` (services list, `migration_strategy.service_layer`, `compat_mode.spring_di`)
- Source service files

## Transformation Rules

Apply RULE GROUP 2 from `transformation_rules.md`.

---

## Step 0 — Read `migration-spec.yaml` and route to the correct path

Read `migration_strategy.service_layer` from the spec, then **continue in the appropriate file**:

| Value | Continue with |
|-------|---------------|
| `spring-di-compat` | [`service-compat.md`](service-compat.md) |
| `application-scoped-cdi` (or any other value) | [`service-full-migration.md`](service-full-migration.md) |

Each sub-file is self-contained from Step 1 onward.

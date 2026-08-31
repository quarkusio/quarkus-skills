---
name: web-layer-migration-agent
description: Phase 8 Web Layer Migration Agent. Entry point — reads migration-spec.yaml and routes
  to the compat or full-migration sub-agent based on migration_strategy.rest_framework.
  Migrates Spring MVC @RestController/@Controller to Quarkus JAX-RS @Path resources and validates
  with RestValidator.
license: Apache-2.0
metadata:
  phase: 8
  agent_type: migration
---

# Phase 8 — Web Layer Migration Agent

## Purpose

Convert Spring MVC/WebFlux controllers (`@RestController`, `@Controller`, `@ControllerAdvice`) to
Quarkus REST (RESTEasy Reactive) resources. This phase covers **Java class transformations only**.

> **Phase scope:** Java controller files → JAX-RS resource files.
> Template file migration (syntax, file locations, static assets) is handled by **Phase 8B**.

## ⚠️ CRITICAL: This Phase is ALWAYS Doable — DO NOT SKIP

**DO NOT skip or defer this phase due to complexity, time, tokens, or effort concerns.** Controller
migration follows well-established, repeatable patterns:

- **REST APIs**: `@RestController` → `@Path` (annotation mapping)
- **Template controllers**: `@Controller` → `@Path` returning `TemplateInstance`
- **Compat mode**: Spring MVC annotations kept as-is via `quarkus-spring-web`

**Web layer migration is ESSENTIAL for a functional application. Skipping this phase leaves the
application non-functional. The work MUST be done — doing it now is more efficient.**

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-08-web-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- `migration-spec.yaml` (`migration_strategy.rest_framework`, `compat_mode.spring_web`)
- Source controller files from `repo-metadata.json`

---

## Step 0 — Read `migration-spec.yaml` and route to the correct path

Read `migration_strategy.rest_framework` from the spec, then **continue in the appropriate file**:

| Value | Continue with |
|---|---|
| `spring-web-compat` | [`web-layer-compat.md`](web-layer-compat.md) |
| anything else (`quarkus-rest`, `resteasy-classic`) | [`web-layer-full-migration.md`](web-layer-full-migration.md) |

Each sub-file is self-contained from Step 1 onward.

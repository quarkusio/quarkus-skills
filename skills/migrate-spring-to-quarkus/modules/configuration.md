---
name: configuration-migration-agent
description: Phase 9 Configuration Migration Agent. Entry point — reads migration-spec.yaml and routes
  to the compat or full-migration sub-agent based on migration_strategy.service_layer and
  migration_strategy.config_properties.
license: Apache-2.0
metadata:
  phase: 9
  agent_type: migration
---

# Phase 9 — Configuration Migration Agent

## Purpose

Migrate Spring Boot configuration to Quarkus configuration.

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-09-configuration-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- `migration-spec.yaml`
- Source `application.properties` / `application.yml`
- Source `@Configuration` classes

## Shared: Property Migration

The following property mappings apply to **both** paths and must always be applied:

```properties
# Spring → Quarkus
server.port                        → quarkus.http.port
spring.application.name            → quarkus.application.name
spring.datasource.url              → quarkus.datasource.jdbc.url
spring.jpa.hibernate.ddl-auto      → quarkus.hibernate-orm.database.generation
spring.kafka.bootstrap-servers     → kafka.bootstrap.servers
```

Ensure no `spring.*` properties remain in the Quarkus config file after migration.

---

## Step 0 — Read `migration-spec.yaml` and route to the correct path

Read `migration_strategy.service_layer` and `migration_strategy.config_properties` from the spec,
then **continue in the appropriate file**:

| Condition | Continue with |
|-----------|---------------|
| `service_layer = spring-di-compat` **OR** `config_properties = spring-boot-properties-compat` | [`09a-configuration-compat.prompt.md`](09a-configuration-compat.prompt.md) |
| Neither flag is active (any other value or absent) | [`09b-configuration-full-migration.prompt.md`](09b-configuration-full-migration.prompt.md) |

Each sub-file is self-contained from Step 1 onward.

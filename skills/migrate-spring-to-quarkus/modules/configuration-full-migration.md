---
name: configuration-full-migration-agent
description: Phase 9B Configuration Migration — Full Migration. Migrates application.properties to
  Quarkus, rewrites @Configuration/@Bean classes to CDI @ApplicationScoped/@Produces, converts
  @ConfigurationProperties to @ConfigMapping interfaces, and adds lifecycle hooks.
  Called from 09-configuration.prompt.md when no compat flags are active.
license: Apache-2.0
metadata:
  phase: 9
  agent_type: migration
---

# Phase 9B — Configuration Migration: Full Migration

> **Entry point:** This file is invoked by `09-configuration.prompt.md` when neither
> `migration_strategy.service_layer = spring-di-compat` nor
> `migration_strategy.config_properties = spring-boot-properties-compat` is active.
> Output file location, inputs, and the shared Property Migration table are defined in the
> entry-point file — read those first.

> **Execution mode:** This module applies deterministic property and class transformations and
> contains no user decision gates. It reads `execution.mode` from `migration-spec.yaml` only for
> error handling: on a compile failure it delegates to
> [`modules/testing/compile-fix.md`](../testing/compile-fix.md) (3 retries per file); if still
> failing, **`interactive`** pauses and asks the user, **`autonomous`** records the failure under
> `unresolved_issues:` and continues — no prompts in either path.

## Overview

No compat bridge extensions are used. All Spring configuration constructs are rewritten to their
Quarkus / CDI / MicroProfile equivalents.

---

## Step 1 — Migrate `application.properties` / `application.yml`

Apply the shared Property Migration table from the entry-point file.
Ensure no `spring.*` properties remain in the Quarkus config file.

---

## Step 2 — Migrate `@Configuration` classes to CDI `@Produces`

For each `@Configuration` class in the source:

1. Replace `@Configuration` with `@ApplicationScoped`
2. Replace each `@Bean` method annotation with `@Produces`
3. Replace `RestTemplate` return types with a JAX-RS `Client`
4. Update imports (remove Spring, add Jakarta/JAX-RS)
5. Write to target (preserving package structure)

```java
// Before (Spring)
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

// After (Quarkus)
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

@ApplicationScoped
public class AppConfig {
    @Produces
    public Client restClient() {
        return ClientBuilder.newClient();
    }
}
```

**Import reference — remove:**
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
```

**Import reference — add (as needed per file):**
```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
```

---

## Step 3 — Migrate `@ConfigurationProperties` classes to `@ConfigMapping` interfaces

For each `@ConfigurationProperties` class in the source, create a `@ConfigMapping` interface:

```java
// Before (Spring)
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String name;
    private int timeout;
    private Map<String, String> labels = new HashMap<>();

    // getters and setters ...
}

// After (Quarkus)
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "app")
public interface AppProperties {
    String name();
    int timeout();
    Map<String, String> labels();
}
```

Key rules:
- Replace the class with an `interface`
- Remove all fields, constructors, getters, and setters
- Each former getter becomes a no-arg interface method with the same return type
- `Map<K,V>` fields map directly to `Map<K,V>` interface methods — no special handling needed
- Remove `@ConstructorBinding` (not applicable to interfaces)
- Update injection sites: replace `@Autowired AppProperties props` with `@Inject AppProperties props`
  (the interface is injected directly)

---

## Step 4 — Add lifecycle hooks

If the source application uses `@EventListener(ApplicationReadyEvent.class)`,
`CommandLineRunner`, or `ApplicationRunner`, replace with Quarkus `StartupEvent` /
`ShutdownEvent` observers:

```java
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class AppLifecycle {
    void onStart(@Observes StartupEvent event) {
        // startup logic
    }

    void onStop(@Observes ShutdownEvent event) {
        // shutdown logic
    }
}
```

---

## Step 5 — Compile

```bash
cd <quarkus_target_dir>
mvn clean package -DskipTests
```

Fix any compilation errors before proceeding.

---

## Validation Gate

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java && mvn clean package -DskipTests -q

# Extract config metadata (optional — validator can read application.properties directly)
java -jar target/migration-validator-1.0.0.jar extract server-config \
  <spring_source_dir> <spring_source_dir>/migration-metadata/spring-config-metadata.json
java -jar target/migration-validator-1.0.0.jar extract server-config \
  <quarkus_target_dir> <quarkus_target_dir>/migration-metadata/quarkus-config-metadata.json

# Run full-migration validator
java -jar target/migration-validator-1.0.0.jar validate config \
  <spring_source_dir> \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in `application.properties` / `@Configuration` classes
  3. Rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks (full migration):**
- Property mappings applied (`server.port → quarkus.http.port`, `spring.datasource.* → quarkus.datasource.*`, etc.)
- No `spring.*` properties remain in the Quarkus config file
- `@Configuration` classes migrated to `@ApplicationScoped` + `@Produces`
- `@ConfigurationProperties` classes migrated to `@ConfigMapping` interfaces
- Maven compile succeeds

### Blocking Criteria

The following issues will block progression to Phase 10:
- Critical Spring properties not migrated to Quarkus equivalents
- `@Configuration` classes not migrated to `@Produces`
- `@ConfigurationProperties` classes not migrated to `@ConfigMapping`
- Missing required Quarkus configuration properties
- Incorrect property value transformations

### Non-Blocking Warnings

These can be addressed later but should be documented:
- Unmapped Spring properties (may not be needed in Quarkus)
- Property value differences (verify intentional)
- Missing optional configuration

**⚠️ IMPORTANT: Do not proceed to Phase 10 until the validation gate passes!**

---

## Output

**Directory setup:**
```bash
mkdir -p <quarkus_target_dir>/migration-reports
```

Write `<quarkus_target_dir>/migration-reports/phase-09-configuration-migration.json`:

```json
{
  "phase": "configuration-migration",
  "status": "completed",
  "strategy": "full-migration",
  "configuration_classes_migrated": 0,
  "config_properties_migrated": 0,
  "lifecycle_hooks_added": 0,
  "properties_migrated": [],
  "files": [],
  "package_status": "PASS"
}
```

Then update `migration-metadata/migration-context.json`:
- Add `"9-config"` to `completedPhases`
- Set `phaseReports["9-config"]` to `"migration-reports/phase-09-configuration-migration.json"`

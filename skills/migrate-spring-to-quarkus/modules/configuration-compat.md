---
name: configuration-compat-agent
description: Phase 9A Configuration Migration — Compat Mode. Keeps Spring @Configuration/@Bean and
  @ConfigurationProperties intact using quarkus-spring-di and quarkus-spring-boot-properties bridges.
  Fixes only the patterns that are NOT bridged (SpEL @Value, RestTemplate beans, Map<K,V> fields,
  @ConstructorBinding). Called from 09-configuration.prompt.md when service_layer = spring-di-compat
  OR config_properties = spring-boot-properties-compat.
license: Apache-2.0
metadata:
  phase: 9
  agent_type: migration
---

# Phase 9A — Configuration Migration: Compat Mode

> **Entry point:** This file is invoked by `09-configuration.prompt.md` when
> `migration_strategy.service_layer = spring-di-compat` **or**
> `migration_strategy.config_properties = spring-boot-properties-compat`.
> Output file location, inputs, and the shared Property Migration table are defined in the
> entry-point file — read those first.

## Overview

One or both compat bridges are active. The relevant Spring configuration constructs are wired at
runtime — **do not rewrite them**. This phase is surgical: verify extensions, migrate
`application.properties`, and fix only the patterns that are not bridged.

> **Minimum Quarkus versions for compat extensions used in this phase:**
> - `quarkus-spring-di` (`service_layer = spring-di-compat`) — available since **0.7.0**
> - `quarkus-spring-boot-properties` (`config_properties = spring-boot-properties-compat`) — available since **1.2.0**
>
> If the target version in `migration-spec.yaml` is older than the minimum for any active compat
> extension:
> - **interactive** — flag the conflict to the user and refer to `references/spring-compat-mode-support.md` for resolution options.
> - **autonomous** — do not prompt; record the conflict under `unresolved_issues:` (severity ERROR) and continue, per SKILL.md → EXECUTION MODE.

Read `execution.mode` from `migration-spec.yaml`; this module otherwise applies deterministic property
mappings and requires no user prompt in either mode.

---

## Step 1 — Verify compat extensions in `pom.xml`

**If `service_layer = spring-di-compat`:** confirm `quarkus-spring-di` is present; add if missing:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-spring-di</artifactId>
</dependency>
```

**If `config_properties = spring-boot-properties-compat`:** confirm `quarkus-spring-boot-properties`
is present; add if missing:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-spring-boot-properties</artifactId>
</dependency>
```

---

## Step 2 — Migrate `application.properties` / `application.yml`

Apply the shared Property Migration table from the entry-point file.
Ensure no `spring.*` properties remain in the Quarkus config file.

---

## Step 3 — Fix unbridged patterns (if `service_layer = spring-di-compat`)

`@Configuration` + `@Bean` classes and property-placeholder `@Value("${prop}")` /
`@Value("${prop:default}")` are **bridged automatically** — do not touch them.

### Fix A — SpEL `@Value` → `@ConfigProperty`

`@Value("#{…}")` (SpEL expression syntax) is **not** bridged. Search for any occurrences and
rewrite each one:

```bash
grep -r '@Value.*#\{' <quarkus_target_dir>/src/main/java --include="*.java"
```

```java
// Before (SpEL — not supported)
@Value("#{systemProperties['user.timezone']}")
private String timezone;

// After (CDI equivalent)
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ConfigProperty(name = "user.timezone", defaultValue = "UTC")
String timezone;
```

### Fix B — Replace `RestTemplate` beans

`quarkus-spring-di` does not bridge `RestTemplate` (a Spring Web concern — no compat bridge exists).
Replace each `RestTemplate` `@Bean` method with a JAX-RS `Client`. The `@Bean` annotation itself
can stay, as it is still wired by `quarkus-spring-di`:

```java
// Before (Spring)
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// After (Quarkus — @Bean still wired by quarkus-spring-di)
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

@Bean
public Client restClient() {
    return ClientBuilder.newClient();
}
```

Search for remaining `RestTemplate` beans:
```bash
grep -r 'RestTemplate' <quarkus_target_dir>/src/main/java --include="*.java"
```

---

## Step 4 — Fix unbridged patterns (if `config_properties = spring-boot-properties-compat`)

`@ConfigurationProperties` classes are **bridged at runtime** — do not rewrite them to
`@ConfigMapping`. Fix only the two patterns below that cause runtime failures.

### Fix C — Replace `Map<K,V>` fields

`Map<K,V>` fields inside a `@ConfigurationProperties` class throw a `DeploymentException` at
startup. Extract the map into a dedicated `@ConfigMapping` interface:

```java
// Before — causes DeploymentException with compat extension
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private Map<String, String> labels = new HashMap<>();
    // getters/setters ...
}

// After — extract the map into its own @ConfigMapping interface
@ConfigMapping(prefix = "app")
public interface AppLabels {
    Map<String, String> labels();
}
```

Search for affected classes:
```bash
grep -rn 'Map<' <quarkus_target_dir>/src/main/java --include="*.java" \
  | grep -l 'ConfigurationProperties'
```

### Fix D — Remove `@ConstructorBinding`

The compat extension requires a **no-arg constructor + setter methods**. Remove
`@ConstructorBinding` and convert any constructor-bound fields to setters:

```java
// Before
@ConfigurationProperties(prefix = "app")
@ConstructorBinding
public class AppProps {
    private final String name;
    public AppProps(String name) { this.name = name; }
}

// After
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

Search for remaining `@ConstructorBinding`:
```bash
grep -r '@ConstructorBinding' <quarkus_target_dir>/src/main/java --include="*.java"
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

# Run compat-mode validator
java -jar target/migration-validator-1.0.0.jar validate config \
  <spring_source_dir> \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml \
  --compat-mode
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in `application.properties` / `@Configuration` classes
  3. Rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks (compat mode):**
- `quarkus-spring-di` present in `pom.xml` (when `service_layer = spring-di-compat`)
- No `RestTemplate` `@Bean` methods remain
- No SpEL `@Value("#{…}")` remains (property placeholder `@Value("${…}")` is fine — bridged)
- `quarkus-spring-boot-properties` present in `pom.xml` (when `config_properties = spring-boot-properties-compat`)
- No `Map<K,V>` fields in `@ConfigurationProperties` classes (when `config_properties = spring-boot-properties-compat`)
- No `@ConstructorBinding` remains (when `config_properties = spring-boot-properties-compat`)
- Property migration applied; no `spring.*` properties remain
- Maven compile succeeds

### Blocking Criteria

The following issues will block progression to Phase 10:
- Required compat extension not present in `pom.xml`
- `RestTemplate` `@Bean` methods remain (no compat bridge — causes `ClassNotFoundException` at runtime)
- SpEL `@Value("#{…}")` remains (not bridged — causes startup failure)
- `Map<K,V>` fields in `@ConfigurationProperties` classes (throws `DeploymentException` at startup)
- `@ConstructorBinding` present (compat extension cannot instantiate the class)
- Critical Spring properties not migrated to Quarkus equivalents

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
  "strategy": "compat",
  "service_layer": "spring-di-compat",
  "config_properties": "spring-boot-properties-compat",
  "spel_values_rewritten": 0,
  "rest_template_beans_replaced": 0,
  "map_fields_extracted": 0,
  "constructor_binding_removed": 0,
  "properties_migrated": [],
  "files": [],
  "package_status": "PASS"
}
```

Then update `migration-metadata/migration-context.json`:
- Add `"9-config"` to `completedPhases`
- Set `phaseReports["9-config"]` to `"migration-reports/phase-09-configuration-migration.json"`

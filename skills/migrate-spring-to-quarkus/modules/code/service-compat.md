---
name: service-compat-agent
description: Phase 6A Service Layer Migration — Compat Mode. Keeps Spring @Service, @Component, and @Autowired intact
  using the quarkus-spring-di bridge. Called from 06-service-migration.prompt.md when service_layer = spring-di-compat.
license: Apache-2.0
metadata:
  phase: 6
  agent_type: migration
---

# Phase 6A — Service Layer Migration: Compat Mode (spring-di-compat)

> **Entry point:** This file is invoked by `service-migration.md` when
> `migration_strategy.service_layer = spring-di-compat`.
> Output file location and inputs are defined in the entry-point file — read those first.

## Overview

`quarkus-spring-di` bridges Spring stereotype and DI annotations at runtime. The following
annotations do **NOT** need rewriting — copy them to the target unchanged:

| Spring annotation | Bridged by `quarkus-spring-di`? |
|---|---|
| `@Service`, `@Component`, `@Repository` | ✅ Yes |
| `@Autowired`, `@Qualifier` | ✅ Yes |
| `@Configuration` / `@Bean` | ✅ Yes (see Phase 9 for config class details) |
| `@Value("${prop}")` / `@Value("${prop:default}")` | ✅ Yes (property placeholder syntax only) |
| `@Value("#{…}")` (SpEL) | ❌ No — must be rewritten |
| `@Primary`, `@Lazy`, `@Profile` | ❌ No — silently ignored, causes runtime failures |
| `@Scheduled` | ❌ No — needs separate extension or rewrite |
| `@Async` | ❌ No — no compat extension exists |
| Spring `@Transactional` import | ❌ No — needs import swap or `quarkus-spring-tx` |

The work in this phase is **surgical**: copy every service file to the target and fix only the
things in the "No" column above.

**Additional compat behaviour to be aware of:**

- **Custom stereotype annotations** — meta-annotations that extend `@Component`, `@Service`, or
  `@Repository` (e.g., `@DomainService`) are auto-detected by `quarkus-spring-di`. Do **not**
  rewrite them; they will be bridged without any changes.
- **`List<T>` multi-bean injection** — `@Autowired List<MyService> services` injects all
  matching beans and works unchanged in compat mode.
- **`@Named`-only bean registration** — a class annotated solely with `@Named("name")` (no
  `@Service`/`@Component`) becomes a `@Singleton` bean in compat mode; no changes needed.

> **Minimum Quarkus version requirements:**
> - `quarkus-spring-di` — available since **0.7.0**
> - `quarkus-spring-scheduled` (optional, for keeping Spring `@Scheduled`) — requires **1.6.0**
> - `quarkus-spring-tx` (optional, for keeping Spring `@Transactional` imports) — requires **3.37.0**
>
> If the target version in `migration-spec.yaml` is older than these minimums:
> - **interactive** — flag the conflict to the user and refer to [spring-compat-mode-support.md](../../references/spring-compat-mode-support.md) for resolution options.
> - **autonomous** — do not prompt; record the conflict under `unresolved_issues:` (severity ERROR) and continue with the affected bridge dropped (migrate that annotation manually as in the Option A path), per SKILL.md → EXECUTION MODE.

> **Execution mode:** This module has project-level "choose one option" decisions (Step 2). Read
> `execution.mode` from `migration-spec.yaml`. In **interactive** mode present the options. In
> **autonomous** mode do NOT ask — pick the option marked *recommended* (or the mode default noted at the
> step) and record the choice + rationale in the spec's `decisions:` section.

---

## Step 1 — Verify `quarkus-spring-di` extension

Confirm `quarkus-spring-di` is present in `pom.xml`. Add it if missing:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-spring-di</artifactId>
</dependency>
```

---

## Step 2 — Decide on `@Transactional` and `@Scheduled` strategy (project-level, do once)

These decisions apply uniformly to all files — make them before the per-file pass.

### `@Transactional`

`quarkus-spring-di` does **not** bridge `org.springframework.transaction.annotation.Transactional`.
Choose one option (autonomous default: **Option A**; if target Quarkus < 3.37.0, Option A is the only valid choice):

**Option A — Migrate import (recommended):** replace
`import org.springframework.transaction.annotation.Transactional` with
`import jakarta.transaction.Transactional` in each affected file. The annotation itself is
unchanged — only the import line changes.

**Option B — Add `quarkus-spring-tx` (keep Spring import as-is):**
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-spring-tx</artifactId>
</dependency>
```
Also set `compat_mode.spring_tx: true` in `migration-spec.yaml`.

If `compat_mode.spring_tx: true` is already set in the spec, no action needed here.

### `@Scheduled`

`quarkus-spring-di` does **NOT** bridge Spring `@Scheduled`. Choose one option (autonomous default:
**Option B** — migrate to the Quarkus `@Scheduled` syntax, the correct end state; use Option A only if
target Quarkus ≥ 1.6.0 and the project has many `@Scheduled` methods where preserving Spring syntax saves
significant effort — but note `fixedDelay` is not bridged):

**Option A — Add `quarkus-spring-scheduled` (keep Spring syntax):**
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-spring-scheduled</artifactId>
</dependency>
```
Also set `compat_mode.spring_scheduled: true` in `migration-spec.yaml`. If already set, skip.

**Option B — Migrate each `@Scheduled` method during the per-file pass (Step 4):**
```java
// Before (Spring)
import org.springframework.scheduling.annotation.Scheduled;
@Scheduled(fixedRate = 5000)
public void updateCache() {}

// After (Quarkus)
import io.quarkus.scheduler.Scheduled;
@Scheduled(every = "5s")
void updateCache() {}
```

---

## Step 3 — Discover all service files (same scope as full migration)

Compat mode operates on the **same set of files** as the full migration path. Every Spring service
class must be copied to the target and checked for the surgical fixes below.

```bash
# Find all @Service annotated classes
grep -r "@Service" <spring_source_dir>/src/main/java --include="*.java"

# Find all @Component annotated classes
grep -r "@Component" <spring_source_dir>/src/main/java --include="*.java"

# Find all classes with @Autowired (may indicate services missed above)
grep -r "@Autowired" <spring_source_dir>/src/main/java --include="*.java"

# Find initial data loaders and startup components
grep -r "InitialLoader\|DataLoader\|SampleData\|Bootstrap\|Seeder\|DataInitializer" \
  <spring_source_dir>/src/main/java --include="*.java"
```

Cross-reference findings with `migration-spec.yaml`. Add any missing files to the services list
before proceeding.

---

## Step 4 — Per-file pass: copy and apply surgical fixes

For **each** service file in the services list:

1. **Copy** the file from `<spring_source_dir>` to `<quarkus_target_dir>` (preserving package structure)
2. **Do NOT touch** `@Service`, `@Component`, `@Repository`, `@Autowired`, `@Qualifier`,
   `@Configuration`, `@Bean`, or `@Value("${…}")` — these are bridged
3. Apply the following fixes as needed:

### Fix A — SpEL `@Value` → `@ConfigProperty`

`@Value("#{…}")` expressions are not supported. Replace each one:

```java
// Before (SpEL — not supported)
@Value("#{systemProperties['user.timezone']}")
private String timezone;

// After (CDI equivalent)
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ConfigProperty(name = "user.timezone", defaultValue = "UTC")
String timezone;
```

Search for any in the file:
```bash
grep '@Value.*#\{' <file>
```

### Fix B — Spring `@Transactional` import

If Option A was chosen in Step 2, replace the import in each affected file:
```java
// Remove
import org.springframework.transaction.annotation.Transactional;

// Add
import jakarta.transaction.Transactional;
```
The `@Transactional` annotation on methods/classes stays unchanged.

### Fix C — Unsupported annotations: `@Primary`, `@Lazy`, `@Profile`

These are silently ignored by `quarkus-spring-di` and will cause runtime failures.

- **`@Primary`** → replace with `@io.quarkus.arc.DefaultBean`, or `@Alternative` + `@Priority`
- **`@Lazy`** → remove entirely (Quarkus beans are lazy-initialised by default)
- **`@Profile`** → replace with `@io.quarkus.arc.profile.IfBuildProfile("profileName")`

```java
// Before
@Service
@Primary
public class PreferredOrderService implements OrderService { }

// After
import io.quarkus.arc.DefaultBean;

@Service
@DefaultBean
public class PreferredOrderService implements OrderService { }
```

### Fix D — `@Scheduled` (if Option B was chosen in Step 2)

Rewrite each `@Scheduled` method using the Quarkus syntax shown in Step 2.

### Fix E — `@Async` → `Uni<T>`

`@Async` has no compat bridge. Convert each async method:

```java
// Before
import org.springframework.scheduling.annotation.Async;
import java.util.concurrent.CompletableFuture;

@Async
public CompletableFuture<Order> processOrder(Long id) {
    return CompletableFuture.completedFuture(order);
}

// After
import io.smallrye.mutiny.Uni;

public Uni<Order> processOrder(Long id) {
    return Uni.createFrom().item(order);
}
```

If `@Async` methods are out of scope for this phase, add the class to the `skip[]` list in
`migration-spec.yaml` and document the outstanding work.

4. **Record** the file in the transformation ledger (filename, fixes applied)

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

java -jar target/migration-validator-1.0.0.jar validate services \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml \
  --compat-mode
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in service classes
  3. Rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks (compat mode):**
- `quarkus-spring-di` present in `pom.xml`
- No SpEL `@Value("#{…}")` remains (property placeholder `@Value("${…}")` is fine — bridged)
- If `compat_mode.spring_tx: true` → `quarkus-spring-tx` present in `pom.xml`; otherwise Spring `@Transactional` import replaced
- If `compat_mode.spring_scheduled: true` → `quarkus-spring-scheduled` present in `pom.xml`; otherwise Spring `@Scheduled` import replaced
- No `@Primary`, `@Lazy`, or `@Profile` remain in source
- Maven compile succeeds

**⚠️ Do not proceed to Phase 7 until validation passes!**

---

## Output

**Directory setup:**
```bash
mkdir -p <quarkus_target_dir>/migration-reports
```

Write `<quarkus_target_dir>/migration-reports/phase-06-service-migration.json`:

```json
{
  "phase": "service-migration",
  "status": "completed",
  "strategy": "spring-di-compat",
  "services_migrated": 15,
  "components_migrated": 5,
  "annotations_replaced": ["@Primary → @DefaultBean", "@Profile → @IfBuildProfile"],
  "async_methods_converted": 0,
  "scheduled_tasks_converted": 0,
  "files": [],
  "package_status": "PASS"
}
```

Then update the `transformations.service-migration` section in `migration-spec.yaml`.

Then update `migration-metadata/migration-context.json`:
- Add `"6-service"` to `completedPhases`
- Set `phaseReports["6-service"]` to `"migration-reports/phase-06-service-migration.json"`

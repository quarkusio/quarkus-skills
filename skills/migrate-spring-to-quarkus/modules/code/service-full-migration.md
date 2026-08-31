---
name: service-full-migration-agent
description: Phase 6B Service Layer Migration — Full Migration. Converts Spring @Service and @Component classes to
  CDI @ApplicationScoped beans. Migrates @Autowired to @Inject and validates with ServiceValidator.
  Called from 06-service-migration.prompt.md for application-scoped-cdi strategy.
license: Apache-2.0
metadata:
  phase: 6
  agent_type: migration
---

# Phase 6B — Service Layer Migration: Full Migration (application-scoped-cdi)

> **Entry point:** This file is invoked by `service-migration.md` when
> `migration_strategy.service_layer` is `application-scoped-cdi` (or any value other than
> `spring-di-compat`).
> Output file location and inputs are defined in the entry-point file — read those first.

> **Execution mode:** This module applies deterministic annotation and import transformations and
> contains no user decision gates. It reads `execution.mode` from `migration-spec.yaml` only for
> error handling: on a compile failure it delegates to
> [`modules/testing/compile-fix.md`](../testing/compile-fix.md) (3 retries per file); if still
> failing, **`interactive`** pauses and asks the user, **`autonomous`** records the failure under
> `unresolved_issues:` and continues — no prompts in either path.

## Overview

All Spring stereotype and DI annotations are replaced with their CDI / Quarkus equivalents. No
compat bridge extensions are used.

---

## Scope Requirements

**⚠️ MANDATORY: Migrate ALL service classes — not just those labelled `@Service`**

You MUST migrate every business service class from the Spring application, including:

1. **`@Service` annotated classes** — all business logic services
2. **`@Component` annotated classes** — all utility components, helpers, and supporting classes
3. **Domain services** — services in domain packages that implement business logic
4. **Infrastructure services** — services that provide technical capabilities (routing, external integrations, etc.)
5. **Application services** — facade services that coordinate between layers
6. **⚠️ Initial data loaders and one-time setup components** — classes that run on startup to
   populate initial data (e.g., `InitialLoader`, `SampleDataGenerator`, `DataBootstrap`)

**Common mistakes to avoid:**
- ❌ Only migrating classes explicitly marked with `@Service`
- ❌ Overlooking `@Component` classes that contain business logic
- ❌ Missing domain service classes that may not have Spring annotations but are injected
- ❌ Forgetting initial data loaders — these are critical for application startup

**Verification checklist (complete before finishing this phase):**
- ✅ All `@Service` classes are migrated
- ✅ All `@Component` classes are migrated
- ✅ All classes with `@Autowired` dependencies are migrated
- ✅ All classes injected into other services are migrated
- ✅ Domain service interfaces and implementations are migrated
- ✅ Infrastructure service implementations are migrated
- ✅ All initial data loaders and startup components are migrated

---

## Key Transformations

### 1. `@Service` → `@ApplicationScoped`
```java
// Before
import org.springframework.stereotype.Service;

@Service
public class OrderService { }

// After
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderService { }
```

### 2. `@Component` → `@ApplicationScoped`
```java
// Before
import org.springframework.stereotype.Component;

@Component
public class HelperComponent { }

// After
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HelperComponent { }
```

### 3. `@Autowired` → `@Inject`
```java
// Before
@Autowired
private OrderRepository orderRepository;

// After
@Inject
OrderRepository orderRepository;
```

### 4. `@Value` → `@ConfigProperty`
```java
// Before
@Value("${app.timeout}")
private int timeout;

// After
@ConfigProperty(name = "app.timeout")
int timeout;
```

### 5. `@Transactional` — update import only
```java
// Before
import org.springframework.transaction.annotation.Transactional;

@Transactional
public void saveOrder(Order order) {}

// After
import jakarta.transaction.Transactional;

@Transactional
public void saveOrder(Order order) {}
```

### 6. `@Async` → `Uni<T>`
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

### 7. `@Scheduled` — update syntax and import
```java
// Before
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(fixedRate = 5000)
public void updateCache() {}

// After
import io.quarkus.scheduler.Scheduled;

@Scheduled(every = "5s")
void updateCache() {}
```

### 8. `@Qualifier` → `@Named`

```java
// Before
@Autowired
@Qualifier("premiumOrderService")
private OrderService orderService;

// After
@Inject
@Named("premiumOrderService")
OrderService orderService;
```

For the bean declaration, add `@Named` alongside `@ApplicationScoped`:

```java
// Before
@Service("premiumOrderService")
public class PremiumOrderService implements OrderService { }

// After
import jakarta.inject.Named;

@ApplicationScoped
@Named("premiumOrderService")
public class PremiumOrderService implements OrderService { }
```

### 9. `@Primary` → `@DefaultBean`

```java
// Before
import org.springframework.context.annotation.Primary;

@Service
@Primary
public class PreferredOrderService implements OrderService { }

// After
import io.quarkus.arc.DefaultBean;

@ApplicationScoped
@DefaultBean
public class PreferredOrderService implements OrderService { }
```

Alternatively, use `@Alternative` + `@Priority` when you need explicit ordering among multiple
candidates:

```java
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
@Priority(1)
public class PreferredOrderService implements OrderService { }
```

### 10. `@Lazy` — remove

`@Lazy` has no CDI equivalent and must be removed. Quarkus beans are lazy by default — normal
`@ApplicationScoped` beans are proxied and only instantiated on first use.

```java
// Before
@Service
@Lazy
public class HeavyService { }

// After — @Lazy removed; behaviour is equivalent
@ApplicationScoped
public class HeavyService { }
```

### 11. `@Conditional*` → `@IfBuildProfile` / `@LookupIfProperty`

```java
// Before
@Service
@ConditionalOnProperty(name = "feature.premium", havingValue = "true")
public class PremiumFeatureService { }

// After
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("premium")
public class PremiumFeatureService { }
```

For property-based conditions, use `@LookupIfProperty`:

```java
import io.quarkus.arc.lookup.LookupIfProperty;

@ApplicationScoped
@LookupIfProperty(name = "feature.premium", stringValue = "true")
public class PremiumFeatureService { }
```

### 12. `@Scope` variants

| Spring | Quarkus |
|---|---|
| `@Scope("singleton")` | `@Singleton` |
| `@Scope("prototype")` | `@Dependent` |
| `@Scope("request")` | `@RequestScoped` |
| `@Scope("session")` | `@SessionScoped` |
| `@Scope("application")` | `@ApplicationScoped` |

```java
// Before
@Service
@Scope("prototype")
public class StatefulProcessor { }

// After
import jakarta.enterprise.context.Dependent;

@Dependent
public class StatefulProcessor { }
```

---

## Import Reference

**Remove:**
```java
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.CompletableFuture;
```

**Add (as needed per file):**
```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.arc.lookup.LookupIfProperty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.transaction.Transactional;
import io.smallrye.mutiny.Uni;
```

---

## Steps

### Step 1 — Comprehensive Service Discovery (MANDATORY)

Before starting migration, perform a thorough discovery of ALL service classes:

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

Cross-reference findings with `migration-spec.yaml` to ensure completeness. If any services are
missing from the spec, add them before proceeding.

### Step 2 — Verify the services list in `migration-spec.yaml`

Confirm the list includes:
- All `@Service` annotated classes
- All `@Component` annotated classes
- All domain service implementations
- All infrastructure service implementations

### Step 3 — Migrate each service file

For each file in the services list:
1. Read the source file
2. Apply the Key Transformations above
3. Update imports (remove Spring, add Quarkus/Jakarta)
4. Write to target
5. Record in transformation ledger

### Step 4 — Verify complete migration

After migrating all services, confirm no Spring annotations remain in the target:
```bash
grep -r "@Service\|@Component\|@Autowired" <quarkus_target_dir>/src/main/java --include="*.java"
```

If any remain, investigate and migrate those classes before continuing.

### Step 5 — Compile

```bash
cd <quarkus_target_dir>
mvn clean package -DskipTests
```

Fix any compilation errors before proceeding.

---

## Special Cases

### Constructor Injection

```java
// Before (Spring)
@Service
public class OrderService {
    private final OrderRepository repository;

    @Autowired
    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}

// After (Quarkus)
@ApplicationScoped
public class OrderService {
    private final OrderRepository repository;

    @Inject
    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

### `@PostConstruct` / `@PreDestroy`

These lifecycle annotations work the same in both frameworks — no rewrite needed. Update the
import to the Jakarta namespace if still using `javax`:

```java
// Keep (update import if javax)
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@PostConstruct
void init() {}

@PreDestroy
void cleanup() {}
```

### Initial Data Loaders (CRITICAL)

**⚠️ One-time setup components must be migrated in this phase.**

Initial data loaders are typically `@Component` classes with `@PostConstruct` methods. In Quarkus
the idiomatic equivalent is an `@ApplicationScoped` bean that observes `StartupEvent`.

```java
// Before (Spring)
@Component
@Profile("!test")
public class SampleDataGenerator {
    @Autowired
    private InitialLoader loader;

    @PostConstruct
    public void loadSampleData() {
        loader.loadData();
    }
}

// After (Quarkus)
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SampleDataGenerator {
    @Inject
    InitialLoader loader;

    void onStart(@Observes StartupEvent ev) {
        loader.loadData();
    }
}
```

**Key points:**
- Replace `@PostConstruct` startup logic with `@Observes StartupEvent`
- Remove `@Profile` annotations; use `@io.quarkus.arc.profile.IfBuildProfile` if profile-guarding
  is still needed
- Ensure `@Transactional` is placed on the method (or class) in the loader if DB writes are involved
- Required imports: `io.quarkus.runtime.StartupEvent`, `jakarta.enterprise.event.Observes`

**Common data loader class name patterns:**
- `InitialLoader` / `DataLoader` — main data loading logic
- `SampleDataGenerator` / `DataBootstrap` — triggers loading on startup
- `DatabaseSeeder` / `DataInitializer` — alternative naming

---

## Validation Gate

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java && mvn clean package -DskipTests -q

java -jar target/migration-validator-1.0.0.jar validate services \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in service classes
  3. Rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks:**
- `@Service` → `@ApplicationScoped` on all migrated classes
- `@Autowired` → `@Inject` on all injection points
- `@Transactional` using `jakarta.transaction` import
- `@Scheduled` migrated to Quarkus syntax
- All services migrated, including initial data loaders
- No missing CDI scope annotations

**Non-blocking warnings** (document and address later):
- Service count mismatches (may be intentional)
- `@Async` not yet converted to `Uni<T>`
- Config properties not fully migrated

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
  "strategy": "application-scoped-cdi",
  "services_migrated": 15,
  "components_migrated": 5,
  "async_methods_converted": 3,
  "scheduled_tasks_converted": 2,
  "files": [],
  "package_status": "PASS"
}
```

Then update the `transformations.service-migration` section in `migration-spec.yaml`.

Then update `migration-metadata/migration-context.json`:
- Add `"6-service"` to `completedPhases`
- Set `phaseReports["6-service"]` to `"migration-reports/phase-06-service-migration.json"`

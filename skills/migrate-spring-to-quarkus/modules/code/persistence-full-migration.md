---
name: persistence-full-migration-agent
description: Phase 5B Persistence Migration — Full Migration. Converts Spring Data JPA repositories to Quarkus Hibernate ORM with Panache
  (panache-repository) or standard EntityManager (hibernate-orm-standard).
  Called from database-and-persistence-migration.md for any strategy other than spring-data-compat.
license: Apache-2.0
metadata:
  phase: 5
  agent_type: migration
---

# Phase 5B — Persistence Migration: Full Migration

> **Entry point:** This file is invoked by `database-and-persistence-migration.md` when
> `migration_strategy.repository_layer` is `panache-repository` or `hibernate-orm-standard`.
> Entity migration rules and database schema mapping are in the entry-point file — read those first.

> **Execution mode:** This module applies deterministic entity and repository transformations and
> contains no user decision gates. It reads `execution.mode` from `migration-spec.yaml` only for
> error handling: on a compile failure it delegates to
> [`modules/testing/compile-fix.md`](../testing/compile-fix.md) (3 retries per file); if still
> failing, **`interactive`** pauses and asks the user, **`autonomous`** records the failure under
> `unresolved_issues:` and continues — no prompts in either path.

## Overview

Repositories are fully rewritten from Spring Data JPA interfaces to either:
- **Panache Repository** (`PanacheRepository<E>`) — recommended, idiomatic Quarkus
- **Standard Hibernate ORM** — `@ApplicationScoped` bean with injected `EntityManager`

The choice is determined by `migration_strategy.repository_layer` in `migration-spec.yaml`.

---

## Step 1 — Migrate entities

Apply the entity migration rules from `database-and-persistence-migration.md` — only where each condition is met:
- **`javax.persistence.*` → `jakarta.persistence.*`:** Only if the project is Spring Boot 2.x. Skip if imports are already `jakarta.*`.
- **`@PersistenceContext` → `@Inject` on `EntityManager`:** Only if `@PersistenceContext` is present. Skip if not found.
- **Verify `@Table` / `@Column` name mappings** against `import.sql` — add where missing, verify where present.
- Record each entity in the transformation ledger.

---

## Step 2 — Migrate repositories

For each repository in the `migration-spec.yaml` repositories list, apply the pattern that matches
`migration_strategy.repository_layer`.

### Handling Custom Repository Implementations (`*RepositoryImpl`)

Spring applications often pair a `JpaRepository` interface with a `*RepositoryImpl` class for
custom queries. When migrating to Panache you MUST **merge** both into a single class.

**Pattern in Spring (two files):**
```java
// File 1: OrderRepository.java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(Long customerId);
    Optional<Order> findByOrderNumber(String orderNumber);
}

// File 2: OrderRepositoryImpl.java
public class OrderRepositoryImpl implements OrderRepository {
    @PersistenceContext
    private EntityManager em;

    public List<Order> findComplexOrders() {
        return em.createQuery("SELECT o FROM Order o WHERE ...", Order.class)
                 .getResultList();
    }
}
```

Steps when encountering this pattern:
1. Identify both the repository interface AND its `*RepositoryImpl` class
2. Merge ALL methods from both files into a single Panache repository
3. Convert Spring Data method names to Panache queries (see translation table below)
4. Migrate custom `EntityManager`-based queries to Panache, or keep `EntityManager` if complex
5. Delete the original `*RepositoryImpl` file

### Option A: Panache Repository (Recommended)

Use when `repository_layer = panache-repository`.

```java
// After (Panache) — ONE merged file replacing both Spring Data files
@ApplicationScoped
public class OrderRepository implements PanacheRepository<Order> {

    // Converted from Spring Data derived query
    public List<Order> findByCustomerId(Long customerId) {
        return find("customerId", customerId).list();
    }

    // Converted from Spring Data derived query
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }

    // Migrated from custom impl — Panache query
    public List<Order> findComplexOrders() {
        return find("SELECT o FROM Order o WHERE ...").list();
    }

    // OR for very complex queries, keep EntityManager:
    // @Inject EntityManager em;
    // return em.createQuery("...", Order.class).getResultList();
}
```

**Key points:**
- ✅ Always check for `*RepositoryImpl` classes and merge them
- ✅ Convert `@PersistenceContext` to `@Inject` if keeping `EntityManager`
- ✅ Use Panache query methods; `EntityManager` is still available for complex queries
- ✅ Delete the separate implementation class after merging

### Option B: Standard Hibernate ORM

Use when `repository_layer = hibernate-orm-standard`.

```java
@ApplicationScoped
public class OrderRepository {

    @Inject
    EntityManager em;

    public List<Order> findByCustomerId(Long customerId) {
        return em.createQuery(
                "SELECT o FROM Order o WHERE o.customerId = :id", Order.class)
            .setParameter("id", customerId)
            .getResultList();
    }

    public Optional<Order> findByOrderNumber(String orderNumber) {
        return em.createQuery(
                "SELECT o FROM Order o WHERE o.orderNumber = :num", Order.class)
            .setParameter("num", orderNumber)
            .getResultStream()
            .findFirst();
    }
}
```

### Spring Data Method Name Translation

| Spring Data Pattern | Panache Query |
|---------------------|---------------|
| `findByField(value)` | `find("field", value).list()` |
| `findByFieldAndOther(v1, v2)` | `find("field = ?1 and other = ?2", v1, v2).list()` |
| `findByFieldOrderByOther(value)` | `find("field", Sort.by("other"), value).list()` |
| `findTopNByField(value)` / `findFirstNByField(value)` | `find("field", value).page(0, N).list()` |
| `findDistinctByField(value)` | `find("SELECT DISTINCT e FROM Entity e WHERE e.field = ?1", value).list()` |
| `countByField(value)` | `count("field", value)` |
| `deleteByField(value)` | `delete("field", value)` |
| `existsByField(value)` | `count("field", value) > 0` |
| `findByField(value, Pageable)` | `find("field", value).page(Page.of(p.getPageNumber(), p.getPageSize())).list()` |
| `findByField(value, Sort)` | `find("field", Sort.by(sort.getOrderFor("f").getProperty()), value).list()` |

**`@Query` annotations:**
- JPQL `@Query`: Keep the JPQL string as-is in a Panache `find()`/`list()` call. Change named parameters from `:name` to `?1`, `?2` positional style, or use `Parameters.with("name", value)`.
- Native SQL `@Query(nativeQuery = true)`: Use `getEntityManager().createNativeQuery("...", EntityClass.class).getResultList()` — Panache does not wrap native queries.

**Projection interfaces and `@Value` SPEL in Spring Data:**
Panache does not support projection interfaces. Options:
- Return the full entity and let the caller select fields (simplest).
- Create a plain DTO record/class and use a constructor JPQL expression: `find("SELECT new com.example.MyDto(e.field1, e.field2) FROM Entity e WHERE ...")`.

Record each repository in the transformation ledger.

---

## Step 3 — Verify `application.properties` datasource config

Phase 4 already converted all Spring datasource properties to Quarkus equivalents. Verify they
are present and intact — do not re-convert from the Spring source:

```properties
quarkus.datasource.db-kind=<db-kind from Phase 4>
quarkus.datasource.jdbc.url=<url from Phase 4>
quarkus.datasource.username=<username from Phase 4>
quarkus.datasource.password=<password from Phase 4>

# These must reflect the Phase 4 profiled setup — do NOT flatten to a single unscoped value
%dev.quarkus.hibernate-orm.database.generation=drop-and-create
%prod.quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=true
```

If any properties are missing, restore them from the Phase 4 migration report
(`migration-reports/phase-04-database-migration.json`) rather than re-deriving from the
Spring source (which has already been removed).

---

## Step 4 — Compile the project

```bash
cd <quarkus_target_dir>
mvn clean package -DskipTests
```

Fix any compilation errors before proceeding.

---

## Step 5 — Verify database initialization (MANDATORY)

After migrating entities and repositories, you MUST verify that `import.sql` executes correctly
at runtime.

### Why this step is critical

In Phase 4, `import.sql` was created but could not execute because:
- Quarkus disables Hibernate ORM when no JPA entities are present
- Phase 4 validator can only perform static checks (file exists, config correct)
- Runtime verification requires entities to be present (Phase 5)

Now that entities are migrated, Hibernate ORM will activate and `import.sql` should execute.

### Step 5.1 — Start the application

```bash
cd <quarkus_target_dir>
mvn quarkus:dev
```

### Step 5.2 — Check logs for Hibernate ORM activation

Look for this message in the startup logs:
```
Hibernate ORM core version X.X.X.Final
```

If you see "Hibernate ORM is disabled" instead, STOP and investigate:
- Verify entities have `@Entity` annotation
- Check entities are in correct package structure
- Ensure entities are compiled (check `target/classes`)

### Step 5.3 — Verify `import.sql` execution

With `quarkus.hibernate-orm.log.sql=true` enabled, you should see SQL statements in logs (table and entity names will match your actual project):
```
Hibernate: CREATE TABLE IF NOT EXISTS <your_table> (...)
Hibernate: INSERT INTO <your_table> VALUES (...)
...
```

### Step 5.4 — Verify database content (optional)

If using H2 console or database client, connect and verify. Replace the table names with actual table names from `import.sql`:
```sql
SELECT COUNT(*) FROM <your_primary_table>;    -- Should return > 0
SELECT COUNT(*) FROM <your_secondary_table>;  -- Should return > 0
SELECT * FROM <any_table> LIMIT 5;            -- Should show sample data
```

### Success criteria

✅ **PASS** if ALL of the following are true:
- Hibernate ORM activation message appears in logs
- `import.sql` SQL statements appear in logs (CREATE TABLE, INSERT)
- No SQL errors in logs
- Application starts successfully without database errors

❌ **FAIL** if ANY of the following occur:
- "Hibernate ORM is disabled" message appears
- No SQL statements in logs
- SQL syntax errors in logs
- Application fails to start with database errors

### Troubleshooting

**Problem: "Hibernate ORM is disabled"**
- Cause: No entities found or entities not compiled
- Solution: Verify `@Entity` annotations, check package structure, run `mvn clean compile`

**Problem: "`import.sql` not found"**
- Cause: File in wrong location
- Solution: Ensure file is in `src/main/resources/import.sql` (not in a subdirectory)

**Problem: SQL syntax errors**
- Cause: SQL not compatible with database type
- Solution: Review `import.sql` syntax for target database (H2, PostgreSQL, etc.)

**Problem: Foreign key constraint violations**
- Cause: INSERT statements in wrong order
- Solution: Reorder `import.sql` to insert parent tables before child tables

### Automated verification (optional)

Create this class to check database content programmatically at startup. **Replace `<YourEntity>` with 2–3 actual entity class names from the migrated project** (e.g. `Order`, `Customer`, `Product`).

```java
package org.example.verification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DatabaseVerifier {

    private static final Logger LOG = Logger.getLogger(DatabaseVerifier.class);

    @Inject
    EntityManager em;

    void onStart(@Observes StartupEvent ev) {
        try {
            // Replace <YourEntity1> and <YourEntity2> with actual entity class names
            Long count1 = em.createQuery("SELECT COUNT(e) FROM <YourEntity1> e", Long.class)
                .getSingleResult();
            Long count2 = em.createQuery("SELECT COUNT(e) FROM <YourEntity2> e", Long.class)
                .getSingleResult();

            LOG.infof("Database verification: %d <YourEntity1>, %d <YourEntity2>",
                count1, count2);

            if (count1 == 0 && count2 == 0) {
                LOG.error("Database appears empty! import.sql may not have executed.");
            } else {
                LOG.info("Database initialization verified successfully!");
            }
        } catch (Exception e) {
            LOG.error("Database verification failed", e);
        }
    }
}
```

Place this in `src/main/java/<your/package>/verification/DatabaseVerifier.java`.

---

## Step 6 — Generate migration report

Write `<quarkus_target_dir>/migration-reports/phase-05-persistence-migration.json`:

```json
{
  "phase": "persistence-migration",
  "status": "completed",
  "entities_migrated": 15,
  "repositories_migrated": 12,
  "strategy": "panache",
  "files": [],
  "package_status": "PASS"
}
```

Set `"strategy"` to `"panache"` or `"hibernate-orm-standard"` to match the actual path taken.

Then update the `transformations.persistence-migration` section in `migration-spec.yaml`.

Then update `migration-metadata/migration-context.json`:
- Add `"5-persistence"` to `completedPhases`
- Set `phaseReports["5-persistence"]` to `"migration-reports/phase-05-persistence-migration.json"`

---

## Validation Gate

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java
mvn clean package -DskipTests -q

# Generate metadata (regenerate each time code changes)
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <spring_source_dir> -o <spring_source_dir>/migration-metadata/code-metadata.yaml
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <quarkus_target_dir> -o <quarkus_target_dir>/migration-metadata/code-metadata.yaml

# Run full-migration validation
java -jar target/migration-validator-1.0.0.jar validate persistence \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <migration-spec.yaml>
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in entities/repositories
  3. Regenerate metadata and rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks:** Entity coverage (count + per-entity structure), repository count matches Spring source, `quarkus-spring-data-jpa` present (compat mode), `mvn compile` succeeds

> **Note — not checked by the validator:**
> - `@ApplicationScoped` on Panache repos and Panache query patterns — verify manually by code review
> - `import.sql` runtime execution — this is a manual step (start the app, confirm SQL statements appear in logs); the validator has no runtime access

**⚠️ Do not proceed to Phase 6 until validation passes!**

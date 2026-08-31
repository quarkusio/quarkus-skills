---
name: database-and-persistence-migration-agent
description: Phase 4 & 5 Database and Persistence Migration Agent. Migrates database initialization
  files (import.sql, schema.sql) and configures the datasource (Phase 4), then migrates JPA entities
  and repositories to Quarkus Hibernate ORM — Panache, standard EntityManager, or spring-data-compat
  bridge (Phase 5). Validates with DatabaseMigrationValidator and PersistenceValidator.
license: Apache-2.0
metadata:
  phase: 4-5
  agent_type: migration
---

# Phase 4 & 5 — Database and Persistence Migration Agent

---

# PHASE 4 — Database Migration

## Purpose

Migrate database initialization files (schema.sql, data.sql) from Spring Boot to Quarkus and configure the datasource based on the database type chosen in Phase 2 (Migration Planning).

## ⚠️ Pre-flight: Detect the database initialization approach

Before executing any steps below, inspect the source project to determine which initialization approach it uses. The rest of Phase 4 differs significantly depending on the answer.

### Check 1 — Does the app use Flyway or Liquibase?

Scan `pom.xml` for `flyway-core`, `spring-flyway`, `liquibase-core`, or `spring-liquibase`. Also check `application.yml/properties` for `spring.flyway.*` or `spring.liquibase.*` keys.

**If yes → do NOT use the `import.sql` approach.** Instead:
1. Keep your existing migration files (e.g. `db/migration/V1__*.sql` for Flyway, `db/changelog/` for Liquibase) — they do not need to change.
2. Replace the Spring dependency in `pom.xml`:
   - Flyway: `flyway-core` → `quarkus-flyway`
   - Liquibase: `liquibase-core` → `quarkus-liquibase`
3. Remap the few configuration properties:

   | Spring Property | Quarkus Property |
   |----------------|-----------------|
   | `spring.flyway.locations` | `quarkus.flyway.locations` |
   | `spring.flyway.baseline-on-migrate` | `quarkus.flyway.baseline-on-migrate` |
   | `spring.flyway.enabled` | `quarkus.flyway.enabled` |
   | `spring.liquibase.change-log` | `quarkus.liquibase.change-log` |
   | `spring.liquibase.enabled` | `quarkus.liquibase.enabled` |

4. Skip Steps 1–3 (SQL file migration) below entirely.
5. Proceed directly to **Step 4** (datasource config) and **Step 6** (JDBC driver dependency), then run the validator.

**If no → continue with Step 1 below.**

---

### Check 2 — Does the app have any SQL initialization files?

After confirming no Flyway/Liquibase, scan for any of the following files:
```
src/main/resources/schema.sql
src/main/resources/data.sql
src/main/resources/schema-*.sql
src/main/resources/data-*.sql
src/main/resources/import.sql
```

**If none found:** the app relies solely on `spring.jpa.hibernate.ddl-auto` (or equivalent) for schema management. In this case:
- Skip Steps 1–3 (no SQL files to migrate, no `import.sql` to create).
- Proceed directly to **Step 4** (datasource config) and **Step 6** (JDBC driver dependency).
- Schema will be generated from JPA entities in Phase 5 via `quarkus.hibernate-orm.database.generation`.

**If files found → continue with Steps 1–3 below.**

---

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the Phase 4 migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-04-database-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- migration-spec.yaml (database type from Phase 2)
- Source database files (schema.sql, data.sql, schema-{db}.sql, etc.)
- Source application.yml/properties (datasource configuration)

## Outputs

- import.sql in src/main/resources (Quarkus standard)
- Quarkus datasource configuration in application.properties
- Database migration report (JSON) at `migration-reports/phase-04-database-migration.json`

## Migration Approach

Quarkus uses a simple, standard approach for database initialization:
- **import.sql** - Placed in `src/main/resources/`, automatically executed on startup
- Works for all databases (H2, PostgreSQL, MySQL, MariaDB, etc.)
- Combines schema and data in one file

## Step 1: Locate Source Database Files

Common locations in Spring Boot projects:
```
src/main/resources/schema.sql
src/main/resources/data.sql
src/main/resources/schema-h2.sql
src/main/resources/data-h2.sql
src/main/resources/schema-postgresql.sql
src/main/resources/import.sql (if already exists)
```

> **Skip this step** if the pre-flight check above determined there are no SQL initialization files.

## Step 2: Read Database Type from Migration Spec

```yaml
# migration-spec.yaml
database:
  type: "h2"  # or "postgresql", "mysql", "mariadb"
  source_files:
    - "src/main/resources/schema-h2.sql"
    - "src/main/resources/data.sql"
```

## Step 3: Combine SQL Files into import.sql

> **Skip this step** if the pre-flight check above determined there are no SQL initialization files.

Merge all schema and data files into a single `import.sql`:

```sql
-- import.sql
-- Database initialization for Quarkus
-- Migrated from Spring Boot: schema-h2.sql, data.sql
-- Database type: H2

-- Schema definitions
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    total DECIMAL(10,2),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Initial data
INSERT INTO users (id, username, email) VALUES (1, 'admin', 'admin@example.com');
INSERT INTO users (id, username, email) VALUES (2, 'user', 'user@example.com');
```

**Important Notes:**
- Use `CREATE TABLE IF NOT EXISTS` for idempotency
- Include all schema definitions first, then data
- Preserve comments from source files
- Ensure foreign key constraints are in correct order

### Multi-environment SQL files

Some Spring Boot projects ship separate SQL files for different environments — for example, `schema-h2.sql` for tests and `schema-postgresql.sql` for production.

**Do not discard the non-primary files.** Instead:
- Place the primary database's SQL in `src/main/resources/import.sql` (used at runtime).
- Place the test database's SQL in `src/test/resources/import.sql` (used by Quarkus test profile automatically).
- If three or more environments exist, discuss with the user which is the primary target before discarding others.

## Step 4: Migrate Datasource Configuration

### Spring Boot → Quarkus Property Mapping

| Spring Property | Quarkus Property | Notes |
|----------------|------------------|-------|
| `spring.datasource.url` | `quarkus.datasource.jdbc.url` | Direct mapping |
| `spring.datasource.username` | `quarkus.datasource.username` | Direct mapping |
| `spring.datasource.password` | `quarkus.datasource.password` | Direct mapping |
| `spring.datasource.driver-class-name` | `quarkus.datasource.jdbc.driver` | Usually auto-detected |
| `spring.datasource.hikari.maximum-pool-size` | `quarkus.datasource.jdbc.max-size` | Connection pool |
| `spring.datasource.hikari.minimum-idle` | `quarkus.datasource.jdbc.min-size` | Connection pool |
| `spring.datasource.hikari.connection-timeout` | `quarkus.datasource.jdbc.acquisition-timeout` | Connection pool |
| `spring.datasource.type` | (remove) | Quarkus uses Agroal; pool type not configurable |
| `spring.datasource.jndi-name` | `quarkus.datasource.jdbc.url=java:comp/env/...` | JNDI lookup; migrate to explicit URL if possible |
| `spring.jpa.hibernate.ddl-auto` | (remove) | Use import.sql instead |
| `spring.jpa.show-sql` | `quarkus.hibernate-orm.log.sql` | Direct mapping |
| `spring.jpa.properties.*` | `quarkus.hibernate-orm.*` | Pass-through Hibernate properties; map key-by-key |
| `spring.sql.init.mode` | (remove) | import.sql auto-runs |
| `spring.sql.init.schema-locations` | (remove) | import.sql auto-runs |
| `spring.sql.init.data-locations` | (remove) | import.sql auto-runs |

> **Profile-specific overrides:** Spring Boot uses separate `application-{profile}.yml` files. Quarkus uses property prefixes (`%dev.`, `%prod.`, `%test.`). For each profile-specific datasource override found (e.g. a different URL in `application-prod.yml`), convert it to the corresponding `%prod.quarkus.datasource.*` property in `application.properties`.

### CRITICAL: Hibernate ORM Configuration for import.sql Execution

**⚠️ IMPORTANT QUARKUS BEHAVIOR:**
Quarkus will **completely disable Hibernate ORM** if no JPA entities are present in the project. When Hibernate ORM is disabled, `import.sql` will **NOT execute**, even if the file exists and datasource is configured correctly.

**Why This Matters:**
- Phase 4 (Database Migration) happens BEFORE Phase 5 (Persistence Migration)
- At this stage, no entities have been migrated yet
- Therefore, `import.sql` will not execute until Phase 5 completes
- This is expected Quarkus behavior, not a configuration error

**Required Configuration:**
You MUST add the following Hibernate ORM configuration to `application.properties` to prepare for entity migration in Phase 5:

```properties
# Hibernate ORM configuration (required for import.sql execution)
# Note: import.sql will only execute after entities are migrated in Phase 5

# Development mode: Drop and recreate schema from entities, then run import.sql
%dev.quarkus.hibernate-orm.database.generation=drop-and-create

# Production mode: No schema generation (use Flyway/Liquibase for production)
%prod.quarkus.hibernate-orm.database.generation=none

# Enable SQL logging to verify import.sql execution
quarkus.hibernate-orm.log.sql=true
```

**Configuration Options for `quarkus.hibernate-orm.database.generation`:**
- `none` - No schema generation (default for production)
- `create` - Create schema on startup, don't drop existing
- `drop-and-create` - Drop existing schema and recreate (recommended for dev with import.sql)
- `update` - Update schema to match entities (use with caution)
- `validate` - Validate schema matches entities, fail if not

**Runtime Verification:**
- Static validation in Phase 4 can only verify file existence and configuration
- **Actual import.sql execution verification happens in Phase 5** after entities are migrated
- Phase 5 agent will start the application and verify SQL statements in logs

### Database-Specific Configuration

**H2 Database — file-based (persistent):**
```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:file:./data/testdb;DB_CLOSE_DELAY=-1
quarkus.datasource.username=sa
quarkus.datasource.password=
%dev.quarkus.hibernate-orm.database.generation=drop-and-create
```

**H2 Database — in-memory (dev/test only):**
```properties
quarkus.datasource.db-kind=h2
%dev.quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
quarkus.datasource.username=sa
quarkus.datasource.password=
%dev.quarkus.hibernate-orm.database.generation=drop-and-create
```

**PostgreSQL:**
```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/mydb
quarkus.datasource.username=postgres
quarkus.datasource.password=postgres

# Connection pool
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
```

**MySQL:**
```properties
quarkus.datasource.db-kind=mysql
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC
quarkus.datasource.username=root
quarkus.datasource.password=root
```

**MariaDB:**
```properties
quarkus.datasource.db-kind=mariadb
quarkus.datasource.jdbc.url=jdbc:mariadb://localhost:3306/mydb
quarkus.datasource.username=root
quarkus.datasource.password=root
```

## Step 5: SQL Syntax Adjustments

### Common Adjustments by Database Type

**H2:**
- `AUTO_INCREMENT` → `GENERATED BY DEFAULT AS IDENTITY`
- `DATETIME` → `TIMESTAMP`
- `MERGE INTO` statements work as-is

**PostgreSQL:**
- `AUTO_INCREMENT` → `SERIAL` or `BIGSERIAL`
- Use `BIGSERIAL` for `BIGINT` primary keys
- `DATETIME` → `TIMESTAMP`

**MySQL/MariaDB:**
- `AUTO_INCREMENT` works as-is
- `DATETIME` works as-is
- Ensure `ENGINE=InnoDB` if specified

### Example Conversion

**Before (Spring Boot - schema-h2.sql):**
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME,
    username VARCHAR(255)
);
```

**After (Quarkus - import.sql for H2):**
```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    created_at TIMESTAMP,
    username VARCHAR(255)
);
```

**After (Quarkus - import.sql for PostgreSQL):**
```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP,
    username VARCHAR(255)
);
```

## Step 6: Add Required Dependencies

Ensure correct JDBC driver in pom.xml based on database type:

**H2:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-h2</artifactId>
</dependency>
```

**PostgreSQL:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```

**MySQL:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-mysql</artifactId>
</dependency>
```

**MariaDB:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-mariadb</artifactId>
</dependency>
```

## Phase 4 Migration Workflow

1. **Read migration-spec.yaml** to get database type
2. **Locate source SQL files** (schema.sql, data.sql, etc.)
3. **Combine into import.sql** with appropriate syntax for database type
4. **Extract datasource config** from Spring application.yml/properties
5. **Convert to Quarkus properties** in application.properties
6. **Add JDBC driver dependency** if not already present
7. **Generate migration report**

## Phase 4 Example Migration

**Source (Spring Boot):**
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:file:./data/testdb
    username: sa
    password:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-h2.sql
```

**Target (Quarkus):**
```properties
# application.properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:file:./data/testdb
quarkus.datasource.username=sa
quarkus.datasource.password=

# import.sql automatically detected and executed
```

## Phase 4 Validation Points

After migration, verify:
1. ✅ import.sql exists in src/main/resources (or Flyway/Liquibase files preserved — as applicable)
2. ✅ import.sql contains all migrated SQL content (schema, data, or both as applicable to this app)
3. ✅ SQL syntax is compatible with target database
4. ✅ Datasource configuration is complete
5. ✅ Correct JDBC driver dependency is present
6. ✅ No Spring datasource properties remain

## Phase 4 Report Generation

**Directory Setup:**
```bash
mkdir -p migration-reports
```

**File Location:** `migration-reports/phase-04-database-migration.json`

Generate the report in the target Quarkus project at `<quarkus_target_dir>/migration-reports/phase-04-database-migration.json`:

```json
{
  "phase": "database-migration",
  "status": "success",
  "database_type": "h2",
  "files_migrated": [
    {
      "source": ["schema-h2.sql", "data.sql"],
      "target": "import.sql",
      "lines": 150,
      "tables_created": 5
    }
  ],
  "datasource_config": {
    "db_kind": "h2",
    "url": "jdbc:h2:file:./data/testdb",
    "pool_max_size": 20
  },
  "dependencies_added": ["quarkus-jdbc-h2"],
  "sql_adjustments": [
    "Converted AUTO_INCREMENT to GENERATED BY DEFAULT AS IDENTITY",
    "Converted DATETIME to TIMESTAMP"
  ],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Phase 4 Error Handling

### Common Issues

1. **Multiple schema files for different databases:**
   - Use the one matching the chosen database type
   - Example: If PostgreSQL chosen, use schema-postgresql.sql

2. **Large data files:**
   - If data.sql > 10MB, warn user
   - Consider loading data programmatically instead

3. **Database-specific SQL:**
   - Flag syntax that may not be portable
   - Provide conversion suggestions

4. **Missing datasource config:**
   - Use sensible defaults based on database type
   - Flag for user review

## Phase 4 Validation

**Run validator after completing database migration:**

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java
mvn clean package -DskipTests -q

# Run validator
java -jar target/migration-validator-1.0.0.jar validate database \
  <target_project_root> \
  <target_project_root>/migration-spec.yaml
```

**VALIDATION LOOP (MANDATORY - DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in SQL files/configuration
  3. Rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to Phase 5 when: `Rules: X total | X passed | 0 failed`

**Validator checks:** import.sql exists, datasource config complete, no Spring properties, JDBC driver present, Hibernate ORM config, Maven compile

**⚠️ Note:** Static validation only - runtime import.sql execution verified in Phase 5 after entities are migrated

## Phase 4 Success Criteria

- [ ] import.sql created with all migrated SQL content (schema, data, or both as applicable) — or skipped if app uses Flyway/Liquibase/DDL-auto only
- [ ] Datasource configuration migrated to application.properties
- [ ] Correct JDBC driver dependency added
- [ ] SQL syntax adjusted for target database
- [ ] No Spring datasource properties remain
- [ ] Profile-specific datasource overrides converted to `%prod.`/`%dev.` Quarkus prefixes
- [ ] Database migration report generated
- [ ] **Validator passes all checks (exit code 0)**
- [ ] migration-context.json updated

## Phase 4 Context Update

After the report is written and the validator passes, update `migration-metadata/migration-context.json`:
- Add `"4-database"` to `completedPhases`
- Set `phaseReports["4-database"]` to `"migration-reports/phase-04-database-migration.json"`

---

# PHASE 5 — Persistence Migration

## Purpose

Migrate JPA entities to Quarkus Hibernate ORM. Repository migration depends on the chosen strategy:

- **`spring-data-compat`** — repository interfaces are kept as-is (`JpaRepository<E, ID>`) and bridged at runtime by `quarkus-spring-data-jpa`; no rewrite to Panache
- **`panache-repository`** — repositories are rewritten as `PanacheRepository<E>` beans
- **`hibernate-orm-standard`** — repositories are rewritten as `@ApplicationScoped` beans with an injected `EntityManager`

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the Phase 5 migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-05-persistence-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- `migration-spec.yaml` (entities and repositories lists, `migration_strategy.repository_layer`, `compat_mode.spring_data_jpa`)
- Source entity and repository files

## Transformation Rules

Apply RULE GROUP 3 from `transformation_rules.md`.

---

## ⚠️ Pre-flight: Detect whether Phase 5 applies

Before executing any steps, check whether the source project uses JPA at all.

Scan `pom.xml` for `spring-boot-starter-data-jpa`, `jakarta.persistence`, or `javax.persistence`. Also scan the source Java files for any class annotated `@Entity`.

**If no JPA found:** the app uses a non-JPA data access layer (e.g. `JdbcTemplate`, MyBatis, JOOQ, or plain JDBC). Phase 5 does not apply.
- Record this in the migration report with `"strategy": "no-jpa-skipped"`.
- Proceed directly to Phase 6.

**If JPA found → continue with the steps below.**

---

## Shared: Entity Migration Rules

These rules apply to **both** migration paths.

### Entity Migration

For each entity, apply these changes **only where they are actually needed** — check each condition before acting:

1. **`javax.persistence.*` → `jakarta.persistence.*`:** Only applies to Spring Boot 2.x projects. Spring Boot 3.x already uses `jakarta.persistence.*` — if imports are already Jakarta, skip this step. Do not report it as a change made.
2. **Keep all other JPA annotations unchanged.**
3. **`@PersistenceContext` → `@Inject` on `EntityManager`:** Scan the entity and its companion classes first. If `@PersistenceContext` is not present, skip this rule entirely.
4. **CRITICAL: Verify (and add if missing) `@Table` and `@Column` name mappings** — see Database Schema Mapping section below.

### Database Schema Mapping (CRITICAL)

**⚠️ MANDATORY: Verify that all entities have table and column mappings that match `import.sql`**

When migrating entities, confirm that JPA annotations match the database schema used in `import.sql`. Many well-maintained Spring Boot apps already have explicit `@Table` and `@Column` annotations — in that case, your job is to verify they are correct, not to add them from scratch.

#### Table Name Mapping

**Rule:** If the table name in `import.sql` uses snake_case or differs from the entity class name, and no `@Table` annotation is already present, add one. If `@Table` is already present, verify that `name` matches `import.sql` exactly.

```java
// import.sql uses: INSERT INTO application_settings ...
@Entity
@Table(name = "application_settings")  // ✓ REQUIRED
public class ApplicationSettings { }

// import.sql uses: INSERT INTO carrier_movement ...
@Entity
@Table(name = "carrier_movement")  // ✓ REQUIRED
public class CarrierMovement { }
```

**Without `@Table` annotation:** Hibernate will use its default naming strategy, which may differ from your SQL schema, causing "Table not found" errors at runtime.

#### Column Name Mapping

**Rule:** For each field whose column name in `import.sql` differs from the Java field name (e.g. snake_case vs camelCase), a `@Column(name = "...")` annotation must be present and must EXACTLY match the column name in `import.sql` — including case. If the annotation already exists, verify it; if absent, add it.

**⚠️ CRITICAL: The column name in `@Column` MUST be character-for-character identical to `import.sql`**

> **Validator limitation:** The PersistenceValidator normalises column names to uppercase before comparing. It will **not** catch a case mismatch — for example, `@Column(name = "SAMPLE_LOADED")` and `@Column(name = "sample_loaded")` will both pass validation even if `import.sql` uses lowercase. **Verify column name casing manually** by cross-checking `@Column(name = "...")` against the exact column names in `import.sql` before running the validator.

```java
// import.sql uses: INSERT INTO application_settings (id, sample_loaded) VALUES ...
@Entity
@Table(name = "application_settings")
public class ApplicationSettings {
    @Id
    private Long id;

    @Column(name = "sample_loaded")  // ✓ CORRECT - matches import.sql exactly
    private boolean sampleLoaded;
}

// ❌ WRONG EXAMPLES:
// @Column(name = "SAMPLE_LOADED")  // Wrong - import.sql uses lowercase
// @Column(name = "sampleLoaded")   // Wrong - import.sql uses snake_case
// No @Column annotation            // Wrong - Hibernate will use "sampleLoaded"
```

**Verification Process:**
1. Open `import.sql` and identify the EXACT column name (e.g., `sample_loaded`)
2. Copy the column name character-for-character into `@Column(name = "...")`
3. Do NOT change case, do NOT convert between snake_case/camelCase
4. The annotation value must be a perfect string match to the SQL column name

**Without exact `@Column` annotation:** Hibernate will use the field name as-is, causing "Column not found" errors at runtime.

#### Validation Process

After migrating each entity:

1. **Read `import.sql`** and identify all table names and column names
2. **For each entity:**
   - Check if table name matches class name (case-insensitive)
   - If not, verify `@Table(name = "...")` annotation exists
   - Check each field against the corresponding column in `import.sql`
   - If column uses snake_case, verify `@Column(name = "...")` annotation exists

3. **Common patterns to check:**
   ```java
   // Pattern 1: snake_case table name
   @Table(name = "table_name")

   // Pattern 2: snake_case column name
   @Column(name = "column_name")

   // Pattern 3: Both
   @Entity
   @Table(name = "user_profile")
   public class UserProfile {
       @Column(name = "first_name")
       private String firstName;

       @Column(name = "last_name")
       private String lastName;
   }
   ```

#### Why This Matters

- **Compilation succeeds** even without proper mappings
- **Runtime fails** when Hibernate tries to execute `import.sql`
- Errors only appear during application startup
- Common errors: `Table "TABLE_NAME" not found` or `Column "column_name" not found`

**Example of what happens without proper mapping:**

```java
// ❌ WRONG - Missing @Table annotation
@Entity
public class ApplicationSettings {
    private boolean sampleLoaded;  // ❌ Missing @Column
}
// Runtime error: Table "APPLICATIONSETTINGS" not found

// ✓ CORRECT - With proper annotations
@Entity
@Table(name = "application_settings")
public class ApplicationSettings {
    @Column(name = "sample_loaded")
    private boolean sampleLoaded;
}
```

---

## Step 0 — Read `migration-spec.yaml` and route to the correct path

Read `migration_strategy.repository_layer` from the spec, then **continue in the appropriate file**:

| Value | Continue with |
|-------|---------------|
| `spring-data-compat` | [`persistence-compat.md`](persistence-compat.md) |
| `panache-repository` or `hibernate-orm-standard` | [`persistence-full-migration.md`](persistence-full-migration.md) |

The entity migration rules and database schema mapping above apply in **both** paths.
Each sub-file is self-contained from Step 1 onward.

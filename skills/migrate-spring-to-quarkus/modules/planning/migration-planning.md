---
name: migration-planning-agent
description: Phase 2 Migration Planning Agent. Creates comprehensive migration plan (migration-spec.yaml) based on discovery results.
  Presents technology decisions to user and records choices for subsequent migration phases.
license: Apache-2.0
metadata:
  phase: 2
  agent_type: planning
---

# Phase 2 — Migration Planning Agent

## Purpose

Create a comprehensive migration plan (migration-spec.yaml) based on repository analysis and user decisions.

## Inputs

- migration-metadata/repo-metadata.json (from Discovery Agent)
- migration-metadata/dependency-analysis.yaml (from Dependency Analysis Agent)
- templates/migration-spec-template.yaml
- references/spring-compat-mode-support.md (compat extension details, version availability, known gaps)

## Execution mode

This agent runs under the orchestrator's resolved execution `mode` (`interactive` | `autonomous`), read
from `migration-context.json` / `migration-spec.yaml` (`execution.mode`). See SKILL.md → EXECUTION MODE.

- **interactive** — present the TECHNOLOGY DECISIONS below and wait for the user's answers.
- **autonomous** — do NOT ask. For each decision use the value already resolved (argument /
  `.quarkus-migration.yml` / spec) when present; otherwise choose the best fit for the discovered codebase
  (see AUTONOMOUS SELECTION POLICY) and record it with a one-line rationale in `decisions:`.

Do not conflate the two axes: `mode` is execution (interactive/autonomous); `migration_mode` in the spec is
the **strategy** (full-migration/spring-compatibility). The strategy is resolved the same way — provided
value first, otherwise asked (interactive) or agent-selected (autonomous).

## Steps

1. Read repo-metadata.json and dependency-analysis.yaml
2. Load migration-spec-template.yaml
3. Resolve technology decisions per the execution mode:
   - **interactive** — present technology decisions to the user (see TECHNOLOGY DECISIONS below)
   - **autonomous** — apply provided values and auto-select the rest (see AUTONOMOUS SELECTION POLICY)
4. Populate migration-spec.yaml with:
   - Project metadata
   - Source technology details
   - Target technology configuration
   - Detected features (boolean flags)
   - Component inventories (entities, services, repositories, controllers)
   - Migration strategy based on user decisions
   - Phase configuration
5. **Set `compat_mode.*` flags** based on decisions (see COMPAT MODE FLAGS below)
6. **Add `quarkus-spring-*` extensions** to `target_technology.quarkus_extensions` for each active compat flag
7. Write migration-spec.yaml
8. Update migration-context.json with path

## TECHNOLOGY DECISIONS

**Interactive mode only.** Present these decisions to the user in two stages. Ask Stage 1 first and wait for answers before showing Stage 2. In autonomous mode, do not present these — use them only as the menu of valid values and apply the AUTONOMOUS SELECTION POLICY below.

```
TECHNOLOGY DECISIONS — STAGE 1 (always ask these first)
────────────────────────────────────────────────────────
[1] Target Quarkus version
    1) Latest stable release (recommended — agent resolves it with:
       curl -s https://repo.maven.apache.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml \
         | grep -oP '(?<=<release>)[^<]+' )
    2) Specify a version manually (e.g. "3.15.1")

[2] Target Java version
    1) Java 17 (LTS)
    2) Java 21 (LTS with Virtual Threads)
    3) Other (specify)

[3] Migration mode
    1) Full migration — rewrite all Spring annotations to CDI/JAX-RS/Panache (recommended)
    2) Spring compatibility — keep Spring annotations via Quarkus extension bridges
       ⚠ Still requires manual migration: @Primary, @Lazy, fixedDelay, @ConstructorBinding,
         Map<K,V> fields, NESTED @Transactional propagation, SecurityFilterChain
```

Once the user answers [1]–[3], present the applicable Stage 2 questions:

```
── If [3] = Full migration ──────────────────────────────

[4] Persistence strategy
    1) Hibernate ORM with Panache (recommended - reduces boilerplate)
    2) Hibernate ORM (standard - more control)
    3) Keep Spring Data JPA patterns (requires custom implementation)

[5] Messaging transport (only if messaging detected)
    1) kafka (recommended for Kafka)
    2) amqp (for RabbitMQ)
    3) artemis-jms (for JMS)
    4) in-memory (for testing)
    5) none (remove messaging)

[6] REST framework
    1) Quarkus REST (RESTEasy Reactive) - recommended
    2) RESTEasy Classic
    3) Vert.x Web (for advanced reactive scenarios)

[7] Database strategy
    1) H2 dev + PostgreSQL prod (recommended)
    2) H2 only (for testing)
    3) MySQL
    4) MariaDB
    5) Keep existing database

[8] Security approach (only if Spring Security detected)
    1) None (remove security)
    2) OIDC / Keycloak
    3) Basic authentication
    4) JWT
    5) OAuth2 (separate from OIDC)
    6) LDAP/Active Directory
    7) Custom security (quarkus-security)
    8) mTLS (mutual TLS)

[9] Container target
    1) Docker (JVM fast-jar) - recommended
    2) Docker (native image)
    3) Podman
    4) None

[10] View technology strategy (only if JSP/JSF/Thymeleaf/FreeMarker detected)
    1) Migrate to Qute (recommended — best Quarkus alignment)
    2) Maintain JSF with Quarkus MyFaces (preserves existing JSF investment)
    3) Auto — let agent decide based on file count

[11] Features to explicitly SKIP
    List any features to exclude from migration, or enter 'none'


── If [3] = Spring compatibility ────────────────────────

[4] Messaging transport (only if messaging detected)
    1) kafka (recommended for Kafka)
    2) amqp (for RabbitMQ)
    3) artemis-jms (for JMS)
    4) in-memory (for testing)
    5) none (remove messaging)

[5] Database strategy
    1) H2 dev + PostgreSQL prod (recommended)
    2) H2 only (for testing)
    3) MySQL
    4) MariaDB
    5) Keep existing database

[6] Container target
    1) Docker (JVM fast-jar) - recommended
    2) Docker (native image)
    3) Podman
    4) None

[7] View technology strategy (only if JSP/JSF/Thymeleaf/FreeMarker detected)
    1) Migrate to Qute (recommended — best Quarkus alignment)
    2) Maintain JSF with Quarkus MyFaces (preserves existing JSF investment)
    3) Auto — let agent decide based on file count

[8] Features to explicitly SKIP
    List any features to exclude from migration, or enter 'none'

── Spring compatibility auto-selections (inform the user, do not ask) ──
• Persistence   → quarkus-spring-data-jpa (JpaRepository interfaces kept unchanged)
• REST layer    → quarkus-spring-web (@RestController/@RequestMapping kept unchanged)
                  ⚠ plain @Controller is NOT bridged — only @RestController
• Service layer → quarkus-spring-di (@Service/@Component/@Autowired kept unchanged)
• Scheduling    → quarkus-spring-scheduled (only if @Scheduled detected)
                  ⚠ fixedDelay is NOT bridged — throws IllegalArgumentException at runtime
• Cache         → quarkus-spring-cache (only if @Cacheable/@CacheEvict detected)
                  ⚠ arrays of cache names, key/condition/unless, @Caching, @CacheConfig NOT supported
• Config props  → quarkus-spring-boot-properties (only if @ConfigurationProperties detected)
                  ⚠ @ConstructorBinding and Map<K,V> fields NOT supported
• Transactions  → quarkus-spring-tx (only if Spring @Transactional import detected)
                  ⚠ NESTED propagation → build-time error; readOnly and timeout silently ignored
• Security      → quarkus-spring-security (only if Spring Security detected)
                  ⚠ SecurityFilterChain/WebSecurityConfigurerAdapter are NOT bridged — must still be replaced
```

## AUTONOMOUS SELECTION POLICY

Applies only when `mode = autonomous`. For every decision, **use a provided value if one exists**
(argument / `.quarkus-migration.yml` / already in the spec). Only for values still unresolved, choose the
best fit below, and record each choice + one-line rationale in `decisions:`. Never ask the user.

| Decision | Autonomous default | Rationale basis |
|---|---|---|
| [1] Quarkus version | Latest stable (resolve via the maven-metadata curl in [1]) | Newest features/fixes unless a version was pinned |
| [2] Java version | Match source `java_version` if ≥ 17, else 17 | Preserve the project's runtime; 17 is the floor |
| [3] Strategy (`migration_mode`) | `full-migration`, unless discovery shows a large surface of hard-to-rewrite Spring features (heavy Spring Security config, broad Spring Data usage), in which case `spring-compatibility` | Best long-term fit vs. migration risk |
| [4] Persistence (full only) | `hibernate-orm-panache` when JPA detected | Least boilerplate, idiomatic Quarkus |
| [5] Messaging | Transport matching the detected provider (Kafka→kafka, RabbitMQ→amqp, JMS→artemis-jms); `none` if no messaging | Preserve existing integration |
| [6] REST framework (full only) | `quarkus-rest` (RESTEasy Reactive) | Recommended default |
| [7] Database | `H2 dev + PostgreSQL prod` unless source pins a specific DB, then keep it | Safe dev/prod split |
| [8] Security (full only) | `oidc` if an external IdP is evident, else `custom` (quarkus-security); `none` only if no security detected | Preserve auth posture |
| [9] Container | `Docker (JVM fast-jar)` | Recommended default |
| [10] View technology | Qute when < 5 view files; MyFaces when ≥ 5 JSF files; Qute otherwise | Matches the existing file-count heuristic in Phase 8B |
| [11] Skip | Skip nothing unless a feature is explicitly out of scope in `.quarkus-migration.yml` | Maximize coverage |

After selecting, still run the COMPAT MODE FLAGS derivation and the version gate below exactly as in
interactive mode — only the *asking* is skipped.

## Migration Complexity Assessment

Calculate complexity based on:
- Number of controllers (web layer complexity)
- Number of services (business logic complexity)
- Number of repositories (data access complexity)
- Number of entities (domain model complexity)
- Messaging presence (integration complexity)
- Security presence (cross-cutting concern)

Complexity levels:
- **low**: < 10 components total
- **medium**: 10-50 components
- **high**: 50-100 components
- **very_high**: > 100 components

## COMPAT MODE FLAGS

After collecting decisions, derive `compat_mode.*` flags and the extensions list using these rules:

**When `migration_mode = full-migration`:** set all `compat_mode.*` flags to `false`.

**When `migration_mode = spring-compatibility`:** set each flag to `true` only when the corresponding
feature is present in the source project; otherwise `false`:

| `compat_mode` flag        | Set to `true` when …                                                   | Extension to add                 | Min Quarkus |
|---------------------------|------------------------------------------------------------------------|----------------------------------|-------------|
| `spring_di`               | any `@Service` or `@Component` classes present                         | `quarkus-spring-di`              | 0.7.0       |
| `spring_web`              | `detected_features.spring_web = true`                                  | `quarkus-spring-web`             | 0.21.0      |
| `spring_data_jpa`         | `detected_features.spring_data_jpa = true`                             | `quarkus-spring-data-jpa`        | 0.23.0      |
| `spring_scheduled`        | `detected_features.spring_scheduled = true`                            | `quarkus-spring-scheduled`       | 1.6.0       |
| `spring_cache`            | `detected_features.spring_cache = true`                                | `quarkus-spring-cache`           | 1.5.0       |
| `spring_boot_properties`  | any `@ConfigurationProperties` classes present                         | `quarkus-spring-boot-properties` | 1.2.0       |
| `spring_tx`               | any `org.springframework.transaction.annotation.Transactional` imports | `quarkus-spring-tx`              | 3.37.0      |
| `spring_security`         | `detected_features.spring_security = true`                             | `quarkus-spring-security`        | 1.1.0       |

### ⚠️ Version gate — compat extensions vs. target Quarkus version

After deriving the active compat flags, cross-check each against the **Min Quarkus** column above.
If the resolved `target_technology.quarkus_version` is **older** than the minimum for any active
extension, resolve the conflict according to mode:

- **interactive** — warn the user before writing migration-spec.yaml and wait for their choice (below).
- **autonomous** — prefer option **A** automatically: bump `target_technology.quarkus_version` to the
  highest required minimum among active extensions, and record the bump in `decisions:` with rationale. If
  a version was explicitly pinned (argument or `.quarkus-migration.yml`) and cannot be bumped, fall back to
  option **B** (drop that one compat bridge and migrate the feature manually), and record the affected
  feature under `unresolved_issues:`.

The interactive warning:

```
⚠️  COMPAT VERSION CONFLICT
The following Spring compat extensions are not available in Quarkus <version>:

  • quarkus-spring-tx  (available since 3.37.0, you chose <version>)

Options:
  A) Upgrade the target Quarkus version to 3.37.0 or later (recommended)
  B) Drop the compat bridge — migrate Spring @Transactional to jakarta.transaction.Transactional manually
  C) Specify a different Quarkus version

Reply with A, B, C, or a specific version number.
```

In interactive mode, do NOT write migration-spec.yaml until the conflict is resolved. In autonomous mode,
apply the automatic resolution above and continue.
For the full extension version table and compat details, see references/spring-compat-mode-support.md.

All `compat_mode.*` flags must be explicitly set to `true` or `false` (never null).
Add the corresponding `quarkus-spring-*` extension to `target_technology.quarkus_extensions` for every flag
that is `true`.

Also set these `migration_strategy` fields automatically when `migration_mode = spring-compatibility`:
- `service_layer: spring-di-compat`  (if spring_di flag is true)
- `rest_framework: spring-web-compat`  (if spring_web flag is true)
- `repository_layer: spring-data-compat`  (if spring_data_jpa flag is true)
- `security_approach: spring-security-compat`  (if spring_security flag is true)

## Output Contract

File: `migration-spec.yaml`

Must include:
- All sections from template populated
- User decisions recorded in `decisions:` section
- Phase configuration with enabled flags
- Component inventories from repo-metadata.json
- Quarkus extensions list from dependency-analysis.yaml
- `migration_strategy.migration_mode` set to `full-migration` or `spring-compatibility`
- `compat_mode.*` flags all set to `true` or `false` (auto-derived — see COMPAT MODE FLAGS above)
- `migration_strategy.rest_framework` set (auto-set when compat; user-chosen when full migration)
- `migration_strategy.security_approach` set (if security detected and full migration chosen)

When writing `migration-metadata/migration-context.json`:
- Set `paths.migrationSpec` to the path of `migration-spec.yaml`
- Add `"2-planning"` to `completedPhases`

## Validation

Before completing:
1. `target_technology.quarkus_version` is set (not null) — either from latest stable lookup or user-specified value
2. All detected_features flags are boolean (not null)
3. All user decisions recorded
4. Phase enabled flags set correctly
5. Quarkus extensions match detected features
6. migration-spec.yaml is valid YAML
7. `migration_strategy.migration_mode` is set to `full-migration` or `spring-compatibility`
8. All `compat_mode.*` flags are boolean (not null) — 8 flags: `spring_di`, `spring_web`, `spring_data_jpa`, `spring_scheduled`, `spring_cache`, `spring_boot_properties`, `spring_tx`, `spring_security`
9. If any `compat_mode.*` flag is `true`, the corresponding `quarkus-spring-*` extension is present in `target_technology.quarkus_extensions`
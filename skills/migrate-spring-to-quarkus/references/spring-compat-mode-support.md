# Spring Compatibility Mode Support

This document describes the compat-mode support built into the migration toolchain.

---

## What is compat mode?

Quarkus ships a set of `quarkus-spring-*` extensions that bridge Spring APIs at runtime,
letting a Spring Boot application run on Quarkus without rewriting every annotation.
This is called **compat mode**.

### When to use compat mode vs. full migration

| Situation | Recommendation |
|---|---|
| Large codebase, tight deadline, incremental approach | Compat mode — migrate layer by layer |
| New project or greenfield rewrite | Full migration — no Spring runtime overhead |
| Team unfamiliar with CDI/JAX-RS | Compat mode — reduce learning curve |
| Native image / GraalVM target | Full migration — compat extensions have limited native support |
| Long-term maintenance | Full migration — compat extensions are not the end state |

Compat mode is **per-layer** — you can use Spring Data JPA compat while fully rewriting the web layer,
or any other combination.

---

## Extension version availability

The table below shows the minimum Quarkus version in which each compat extension first shipped.
Any project targeting an older Quarkus release must either upgrade or fully migrate that layer.

| Extension | First Quarkus release |
|---|---|
| `quarkus-spring-di` | 0.7.0 |
| `quarkus-spring-web` | 0.21.0 |
| `quarkus-spring-data-jpa` | 0.23.0 |
| `quarkus-spring-security` | 1.1.0 |
| `quarkus-spring-boot-properties` | 1.2.0 |
| `quarkus-spring-cloud-config-client` | 1.3.0 |
| `quarkus-spring-cache` | 1.5.0 |
| `quarkus-spring-scheduled` | 1.6.0 |
| `quarkus-spring-data-rest` | 1.11.0 |
| `quarkus-spring-tx` | 3.37.0 |

> ⚠️ `quarkus-spring-tx` only appeared in **3.37.0**. Projects on older Quarkus must use the full
> `jakarta.transaction.Transactional` migration for `@Transactional` — the compat bridge is not available.

---

## Per-layer compat extensions

### `quarkus-spring-di` — Service layer

Activated by: `migration_strategy.service_layer: spring-di-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `@Service`, `@Component`, `@Repository` | `@Value("#{spel.expression}")` → SpEL is NOT supported; rewrite to CDI |
| `@Autowired` field/constructor/setter injection | `@Async` → must become `Uni<T>` or be documented |
| `@Configuration` + `@Bean` | `@Scheduled` → must become Quarkus `@Scheduled` (or add `quarkus-spring-scheduled`) |
| `@Qualifier` | `@Profile` → must become `@IfBuildProfile` |
| `@Value("${prop}")` / `@Value("${prop:default}")` — property placeholder syntax | `@Primary` → NOT processed; use `@io.quarkus.arc.DefaultBean` or `@Alternative` + `@Priority` |
| | `@Lazy` → NOT processed; remove annotation (Quarkus beans are lazy-initialised by default) |
| | Spring `@Transactional` **import** → must be `jakarta.transaction.Transactional` (or add `quarkus-spring-tx`) |

### `quarkus-spring-web` — Web layer

Activated by: `migration_strategy.rest_framework: spring-web-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `@RestController` (**only** — plain `@Controller` is NOT bridged) | `@Controller` (plain/template-serving) → rewrite to `@Path` + `TemplateInstance` |
| `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc. | `RestTemplate` / `RestClient` → JAX-RS Client |
| `@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestHeader` | `HandlerInterceptor` → `ContainerRequestFilter` |
| `ResponseEntity` | `WebMvcConfigurer` → Quarkus HTTP config |
| `@ResponseStatus` | `@ControllerAdvice` (global) → JAX-RS `@Provider ExceptionMapper` |
| `@ExceptionHandler` (method-level, partial) | `MultipartResolver` → `@MultipartForm` |

### `quarkus-spring-data-jpa` — Persistence layer

Activated by: `migration_strategy.repository_layer: spring-data-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `JpaRepository`, `CrudRepository`, `PagingAndSortingRepository` interfaces | `javax.persistence.*` → must become `jakarta.persistence.*` |
| Spring Data derived query method names | Spring `@Transactional` import → `jakarta.transaction.Transactional` |
| `@Query` JPQL/named queries | `@PersistenceContext` → must become `@Inject` |
| `Pageable`, `Page`, `Sort` | Custom `*RepositoryImpl` classes (limited support — verify or rewrite) |

### `quarkus-spring-security` — Security layer

Activated by: `migration_strategy.security_approach: spring-security-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `@PreAuthorize` (limited SpEL — see below) | `SecurityFilterChain` → replace with Quarkus Security config in `application.properties` |
| `@Secured("ROLE_X")` | `WebSecurityConfigurerAdapter` → replace with Quarkus Security / OIDC / JWT config |
| `@RolesAllowed("X")` | `SecurityContextHolder` → inject `io.quarkus.security.identity.SecurityIdentity` |
| `@EnableWebSecurity` (not needed — remove) | `UserDetailsService` → implement `SecurityIdentityAugmentor` |
| | `@PostAuthorize` → NOT supported |
| | `@PreFilter` / `@PostFilter` → NOT supported |
| | Cannot mix `@Secured` + `@PreAuthorize` on the same method |

**Supported `@PreAuthorize` SpEL expressions:**
- `permitAll()`, `denyAll()`, `isAuthenticated()`, `isAnonymous()`
- `hasRole('ROLE')`, `hasAnyRole('R1', 'R2')`
- `@beanName.methodName()` — bean method returning boolean
- `#paramName == authentication.principal.username` — compare parameter with current user
- `expr1 and expr2`, `expr1 or expr2` — **cannot mix `and` and `or` in the same expression**
- Full SpEL is NOT supported — only the specific patterns above

### `quarkus-spring-scheduled` — Scheduling

Activated by: `migration_strategy.scheduling: spring-scheduled-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `@Scheduled(cron = "...")` | `fixedDelay` → throws `IllegalArgumentException` at **runtime**; migrate to Quarkus `@Scheduled(every="...")` |
| `@Scheduled(fixedRate = 1000)` | `initialDelay` combined with `cron` → unsupported; split into separate scheduled methods |
| `@Schedules({...})` | `@Async` on scheduled methods → not bridged |
| `@EnableScheduling` (not needed — remove) | |
| `initialDelay` (standalone, without cron) | |

### `quarkus-spring-cache` — Caching

Activated by: `migration_strategy.caching: spring-cache-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `@Cacheable("name")` — **single name only** | `@Cacheable({"name1","name2"})` — arrays not supported; split into separate methods |
| `@CacheEvict("name")` | `key`, `condition`, `unless`, `keyGenerator`, `cacheManager` parameters → silently ignored |
| `@CacheEvict(allEntries = true)` | `@Caching({...})` → NOT supported |
| `@CachePut("name")` ⚠️ | `@CacheConfig` → NOT supported |
| `@EnableCaching` (not needed — remove) | |

> **⚠️ `@CachePut` warning:** The bridge implements `@CachePut` as an invalidate-then-recache sequence.
> This is **not atomic** — concurrent requests can observe a cache miss between the invalidation and re-population.
> In high-concurrency scenarios, migrate to Quarkus `@CacheInvalidate` + `@CacheResult` with proper locking.

### `quarkus-spring-boot-properties` — Configuration Properties

Activated by: `migration_strategy.config_properties: spring-boot-properties-compat`

| Bridged (no change needed) | NOT bridged (must migrate) |
|---|---|
| `@ConfigurationProperties(prefix="app")` — class-based with no-arg constructor + setters | `@ConstructorBinding` → NOT supported; compat requires no-arg constructor + setters |
| `@Validated` on config class | `Map<K,V>` fields → throws `DeploymentException` at startup; replace with `@ConfigMapping` interface |
| `@EnableConfigurationProperties` (not needed — remove) | Interface-based with `@ConfigProperty` for custom names |
| Nested objects (recursively supported) | |
| `List<T>`, `Set<T>` for primitives/enums | |

> **`Map<K,V>` migration:** Replace the `@ConfigurationProperties` class with a `@ConfigMapping` interface
> that exposes a `Map<String, String>` method — this works in native Quarkus config without compat.

---

## How to activate compat mode during planning (Phase 2)

In the Phase 2 planning decisions (Stage 1), select option 2 for question [3]:

```
[3] Migration mode → option 2 (Spring compatibility)
```

That single choice is all that's needed. The planning agent will automatically:
1. Inspect `detected_features` to determine which Spring compat extensions apply
2. Set each `compat_mode.*` flag to `true` when the corresponding feature is present
3. Set the `migration_strategy.*` fields (e.g. `rest_framework: spring-web-compat`)
4. Add all applicable `quarkus-spring-*` extensions to `target_technology.quarkus_extensions`

Example resulting spec section (app with web, DI, data JPA, and security — but no scheduling/cache/config-props/tx):

```yaml
migration_strategy:
  migration_mode: spring-compatibility
  service_layer: spring-di-compat
  repository_layer: spring-data-compat
  rest_framework: spring-web-compat
  security_approach: spring-security-compat

compat_mode:
  spring_di: true
  spring_web: true
  spring_data_jpa: true
  spring_scheduled: false
  spring_cache: false
  spring_boot_properties: false
  spring_tx: false
  spring_security: true

target_technology:
  quarkus_extensions:
    - quarkus-spring-di
    - quarkus-spring-web
    - quarkus-spring-data-jpa
    - quarkus-spring-security
    - quarkus-jdbc-postgresql
```

---

## What each agent phase does differently in compat mode

### Phase 5 — Persistence (`database-and-persistence-migration.md`)

| Full migration | Compat mode (`spring-data-compat`) |
|---|---|
| Convert `JpaRepository` interfaces to Panache | Skip repository rewrite entirely |
| Translate Spring Data method names to Panache queries | No translation needed |
| Update `javax→jakarta` entity imports | ✅ Still required |
| Configure Quarkus datasource properties | ✅ Still required |
| Replace `@PersistenceContext` with `@Inject` | ✅ Still required |

### Phase 6 — Service layer (`06-service-migration.prompt.md`)

| Full migration | Compat mode (`spring-di-compat`) |
|---|---|
| `@Service`/`@Component` → `@ApplicationScoped` | Skip — bridged |
| `@Autowired` → `@Inject` | Skip — bridged |
| `@Value("${prop}")` → `@ConfigProperty` | Skip — bridged (property placeholder syntax only) |
| `@Value("#{spel}")` → CDI equivalent | ✅ Still required — SpEL NOT supported |
| Spring `@Transactional` import → `jakarta.transaction` | ✅ Still required (or add `quarkus-spring-tx`) |
| `@Async` → `Uni<T>` | Document as gap; migrate if needed |
| `@Scheduled` → Quarkus `@Scheduled` | Document as gap; migrate if needed (or add `quarkus-spring-scheduled`) |

### Phase 8 — Web layer (`08-web-layer-migration.prompt.md`)

| Full migration | Compat mode (`spring-web-compat`) |
|---|---|
| `@RestController` → `@Path` | Skip — bridged |
| `@GetMapping` → `@GET @Path` | Skip — bridged |
| `@PathVariable` → `@PathParam` | Skip — bridged |
| `ResponseEntity` → `Response` | Skip — bridged |
| `RestTemplate` → JAX-RS Client | ✅ Still required |
| `@ExceptionHandler` (global) → `ExceptionMapper` | Verify; migrate if needed |

### Phase 9 — Configuration (`09-configuration.prompt.md`)

| Full migration | Compat mode (`spring-di-compat`) |
|---|---|
| `@Configuration`/`@Bean` → `@ApplicationScoped`/`@Produces` | Skip — bridged by `quarkus-spring-di` |
| `@Value` in config classes → `@ConfigProperty` | ✅ Still required |
| `RestTemplate` beans → JAX-RS Client `@Produces` | ✅ Still required |

---

## Invoking the validators with `--compat-mode`

### Services validator (Phase 6)

```bash
# Compat mode — checks: quarkus-spring-di in pom, SpEL @Value migrated, @Transactional import replaced
java -jar validators/java/target/migration-validator-1.0.0.jar validate services \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml \
  --compat-mode

# Full migration mode (default)
java -jar validators/java/target/migration-validator-1.0.0.jar validate services \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

### REST validator (Phase 8)

```bash
# Compat mode — uses SpringRestApiExtractor on target; endpoint comparison is unchanged
java -jar validators/java/target/migration-validator-1.0.0.jar validate rest \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml \
  --compat-mode

# Full migration mode (default)
java -jar validators/java/target/migration-validator-1.0.0.jar validate rest \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

### Persistence validator (Phase 5)

```bash
# Compat mode — checks: quarkus-spring-data-jpa in pom, entity structure, datasource config; skips Panache checks
java -jar validators/java/target/migration-validator-1.0.0.jar validate persistence \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml \
  --compat-mode

# Full migration mode (default)
java -jar validators/java/target/migration-validator-1.0.0.jar validate persistence \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

---

## Known gaps and unsupported features per extension

### `quarkus-spring-di`
- `@Async` — no bridge; document or convert to `Uni<T>`
- `@Scheduled` — no bridge; convert to `io.quarkus.scheduler.Scheduled` or add `quarkus-spring-scheduled`
- `@Profile` — no bridge; use `@io.quarkus.arc.profile.IfBuildProfile`
- `@Value` with SpEL (`#{…}`) — no bridge; rewrite to CDI equivalent
- `@Value` with property placeholder (`${…}`) — **bridged**; no change needed
- `@EventListener` / `ApplicationEventPublisher` — no bridge; use CDI `Event<T>` / `@Observes`

### `quarkus-spring-web`
- `RestTemplate` / `RestClient` — no bridge; use JAX-RS Client or MicroProfile REST Client
- `HandlerInterceptor` / `WebMvcConfigurer` — no bridge
- Global `@ControllerAdvice` with `@ExceptionHandler` — no bridge; use JAX-RS `@Provider ExceptionMapper`
- `MultipartResolver` — no bridge; use RESTEasy `@MultipartForm`
- WebSocket support — not covered by this extension

### `quarkus-spring-data-jpa`
- Custom `*RepositoryImpl` classes — limited support; verify or rewrite to `@ApplicationScoped` beans
- `@EntityGraph` — not supported
- `Specifications` / `JpaSpecificationExecutor` — not supported
- Reactive repositories (`ReactiveCrudRepository`) — not supported
- `javax.persistence.*` imports — must still be updated to `jakarta.persistence.*`

### `quarkus-spring-security`
- `SecurityFilterChain` / `WebSecurityConfigurerAdapter` — not bridged; requires Quarkus Security config
- Method security (`@PreAuthorize` with SpEL expressions) — partial support only
- `UserDetailsService` — not bridged

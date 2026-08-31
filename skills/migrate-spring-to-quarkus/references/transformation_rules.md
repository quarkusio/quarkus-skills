# Spring Boot → Quarkus Transformation Rules

**Quick Reference Catalog for Migration Agents**

This document provides a concise catalog of transformation rules. Each agent file contains detailed examples and implementation guidance for its specific phase.

---

## RULE GROUP 1 — DEPENDENCY MIGRATION

**RULE DEP-1:** Remove Spring Boot dependencies (spring-boot-starter-*)  
**RULE DEP-2:** Add Quarkus BOM (io.quarkus.platform:quarkus-bom)  
**RULE DEP-3:** Map Spring starters to Quarkus extensions

| Spring Starter | Quarkus Extension |
|---|---|
| spring-boot-starter-web | quarkus-rest-jackson |
| spring-boot-starter-data-jpa | quarkus-hibernate-orm-panache |
| spring-boot-starter-security | quarkus-security |
| spring-boot-starter-actuator | quarkus-smallrye-health |
| spring-kafka | quarkus-smallrye-reactive-messaging-kafka |
| spring-boot-starter-amqp | quarkus-smallrye-reactive-messaging-amqp |
| spring-boot-starter-validation | quarkus-hibernate-validator |
| spring-boot-starter-cache | quarkus-cache |

---

## RULE GROUP 2 — ANNOTATION MIGRATION

**RULE ANN-1:** @Service → @ApplicationScoped
**RULE ANN-2:** @Component → @ApplicationScoped
**RULE ANN-3:** @Repository → @ApplicationScoped
**RULE ANN-4:** @Autowired → @Inject
**RULE ANN-5:** @Value("${prop}") → @ConfigProperty(name = "prop")
**RULE ANN-6:** @Qualifier("name") → @Named("name") (jakarta.inject.Named)
**RULE ANN-7:** @Primary → @io.quarkus.arc.DefaultBean (or @Alternative + @Priority)
**RULE ANN-8:** @Lazy → remove entirely (Quarkus beans are lazy by default)
**RULE ANN-9:** @Conditional* → @IfBuildProfile or @LookupIfProperty
**RULE ANN-10:** @Scope("singleton") → @Singleton
**RULE ANN-11:** @Scope("prototype") → @Dependent
**RULE ANN-12:** @Scope("request") → @RequestScoped
**RULE ANN-13:** @Scope("session") → @SessionScoped
**RULE ANN-14:** @Scope("application") → @ApplicationScoped

---

## RULE GROUP 3 — PERSISTENCE MIGRATION

**RULE JPA-1:** Update imports: javax.persistence.* → jakarta.persistence.* *(Spring Boot 2.x only — skip if already Jakarta)*
**RULE JPA-2:** Repository migration depends on `migration_strategy.repository_layer`:
  - `panache-repository` → rewrite `JpaRepository` interface to `PanacheRepository` class
  - `hibernate-orm-standard` → rewrite to `@ApplicationScoped` bean with injected `EntityManager`
  - `spring-data-compat` → keep `JpaRepository` interface as-is; no rewrite needed
**RULE JPA-3:** Spring `@Transactional` import → Jakarta `@Transactional` *(scan first; skip if not present. For compat mode, `quarkus-spring-tx` is an alternative — see `references/spring-compat-mode-support.md`)*
**RULE JPA-4:** @PersistenceContext → @Inject EntityManager *(scan first; skip if not present)*

---

## RULE GROUP 4 — WEB LAYER MIGRATION

**RULE WEB-1:** @RestController + @RequestMapping → @Path
**RULE WEB-2:** @GetMapping/@PostMapping/etc → @GET/@POST + @Path
**RULE WEB-3:** @RequestParam → @QueryParam
**RULE WEB-4:** @PathVariable → @PathParam
**RULE WEB-5:** @RequestBody → (implicit, no annotation)
**RULE WEB-6:** ResponseEntity → Response
**RULE WEB-7:** @ExceptionHandler → @Provider ExceptionMapper
**RULE WEB-8:** @RequestHeader → @HeaderParam("name")
**RULE WEB-9:** @CookieValue → @CookieParam("name")
**RULE WEB-10:** @MatrixVariable → @MatrixParam("name")
**RULE WEB-11:** @ResponseStatus → return Response with explicit status code; on exception classes replace with @ServerExceptionMapper
**RULE WEB-12:** @ResponseBody → (remove — implicit in JAX-RS resources)

---

## RULE GROUP 5 — MESSAGING MIGRATION

**RULE MSG-1:** @KafkaListener → @Incoming  
**RULE MSG-2:** KafkaTemplate → @Channel Emitter  
**RULE MSG-3:** @RabbitListener → @Incoming  
**RULE MSG-4:** RabbitTemplate → @Channel Emitter  
**RULE MSG-5:** @JmsListener → @Incoming

---

## RULE GROUP 6 — CONFIGURATION MIGRATION

**RULE CFG-1:** @Configuration + @Bean → @ApplicationScoped + @Produces  
**RULE CFG-2:** RestTemplate → JAX-RS Client
**RULE CFG-3:** Application properties mapping

| Spring Property | Quarkus Property |
|---|---|
| server.port | quarkus.http.port |
| spring.application.name | quarkus.application.name |
| spring.datasource.url | quarkus.datasource.jdbc.url |
| spring.datasource.driver-class-name | quarkus.datasource.jdbc.driver |
| spring.jpa.show-sql | quarkus.hibernate-orm.log.sql |
| spring.jpa.hibernate.ddl-auto | quarkus.hibernate-orm.database.generation |
| spring.kafka.bootstrap-servers | kafka.bootstrap.servers |

---

## RULE GROUP 7 — VIEW LAYER MIGRATION

**RULE VIEW-1:** JSF @ManagedBean → @Named  
**RULE VIEW-2:** JSF scopes → Jakarta CDI scopes  
**RULE VIEW-3:** @ManagedProperty → @Inject  
**RULE VIEW-4:** Thymeleaf syntax → Qute syntax  
**RULE VIEW-5:** Spring MVC @Controller + Model → @Path + TemplateInstance

---

## RULE GROUP 8 — ASYNC & SCHEDULING

**RULE ASYNC-1:** @Async + CompletableFuture → Uni<T>  
**RULE ASYNC-2:** @Scheduled(fixedRate = ms) → @Scheduled(every = "Xs")

---

## RULE GROUP 9 — SECURITY MIGRATION

**RULE SEC-1:** WebSecurityConfigurerAdapter → application.properties auth config  
**RULE SEC-2:** @PreAuthorize("hasRole('X')") → @RolesAllowed("X")

---

## RULE GROUP 10 — VALIDATION

**RULE VAL-1:** javax.validation.* → jakarta.validation.*

---

## RULE GROUP 11 — LIFECYCLE HOOKS

**RULE LIFE-1:** ApplicationRunner → @Observes StartupEvent  
**RULE LIFE-2:** @PreDestroy → @Observes ShutdownEvent

---

## TRANSFORMATION EXECUTION ORDER

1. **Phase 3:** Dependencies (RULE GROUP 1)
2. **Phase 4:** Database migration and datasource setup
3. **Phase 5:** Persistence (RULE GROUP 3)
4. **Phase 6:** Service layer (RULE GROUP 2)
5. **Phase 7:** Messaging (RULE GROUP 5)
6. **Phase 8:** Web layer REST (RULE GROUP 4)
7. **Phase 8b:** Web views (RULE GROUP 7)
8. **Phase 9:** Configuration (RULE GROUP 6)

**Database Sequencing:**
- Database migration creates `import.sql` and datasource config BEFORE entities
- Static verification happens in Phase 4
- Runtime verification (Hibernate ORM activation + SQL execution) happens in Phase 5

---

## RULE APPLICATION GUIDELINES

1. **Apply rules atomically** - one rule at a time per file
2. **Verify compilation** after each rule group
3. **Retry on failure** - up to 3 attempts per file
4. **Mark for manual review** if still failing after retries
5. **Document skipped features** in migration-spec.yaml

---

## AGENT RESPONSIBILITIES

Each agent file contains:
- Detailed transformation examples with before/after code
- Phase-specific validation requirements
- Error handling procedures
- Output report specifications

**Refer to individual agent files for implementation details.**
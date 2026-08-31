---
name: web-layer-compat-agent
description: Phase 8 Web Layer Migration — Compat Mode. Keeps Spring MVC @RestController intact using
  the quarkus-spring-web bridge. Called from 08-web-layer-migration.prompt.md when rest_framework = spring-web-compat.
license: Apache-2.0
metadata:
  phase: 8
  agent_type: migration
---

# Phase 8 — Web Layer Migration: Compat Mode (spring-web-compat)

> **Entry point:** This file is invoked by `08-web-layer-migration.prompt.md` when
> `migration_strategy.rest_framework = spring-web-compat`.
> Output file location and inputs are defined in the entry-point file — read those first.

## Overview

`quarkus-spring-web` bridges Spring MVC annotations at runtime. The following annotations do
**NOT** need rewriting — keep them as-is in the target:

| Spring annotation | Bridged by `quarkus-spring-web`? |
|---|---|
| `@RestController` | ✅ Yes |
| `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` | ✅ Yes |
| `ResponseEntity` | ✅ Yes |
| `@Controller` (plain, non-REST) | ❌ No — silently does not work at runtime |
| `RestTemplate` / `RestClient` | ❌ No — causes `ClassNotFoundException` at runtime |
| `HandlerInterceptor` | ❌ No — must be rewritten |
| `WebMvcConfigurer` | ❌ No — must be rewritten |
| `@ControllerAdvice` (global `@ExceptionHandler`) | ❌ Partial — verify runtime behaviour |
| `MultipartResolver` | ❌ No — must be rewritten |
| `@CrossOrigin` | ❌ No — not processed |
| `@InitBinder` | ❌ No — not processed |
| `@ModelAttribute` (on methods/parameters) | ❌ No — not processed |
| `@SessionAttributes` | ❌ No — not processed |
| `Mono` / `Flux` (reactive return types) | ❌ No — not supported |
| Multiple `@RestControllerAdvice` classes | ❌ Hard limit — only the **first** one discovered is registered; extras are silently ignored |

> **Minimum Quarkus version:** `quarkus-spring-web` is available since **0.21.0**.
> If the target version in `migration-spec.yaml` is older than this minimum:
> - **interactive** — flag the conflict to the user and refer to `references/spring-compat-mode-support.md` for resolution options.
> - **autonomous** — do not prompt; record the conflict under `unresolved_issues:` (severity ERROR) and continue, per SKILL.md → EXECUTION MODE.

> **Execution mode:** Read `execution.mode` from `migration-spec.yaml`. This module's per-class handling in
> Step 3 is deterministic (JSON → `@RestController`; views → `@Path` + Qute) and requires no user prompt in
> either mode. Where a class cannot be classified, record it under `unresolved_issues:` in autonomous mode
> (a WARNING in the report in interactive mode).

---

## Step 1 — Discover all controller files (same scope as full migration)

Before doing anything else, establish the complete list of files this phase operates on.
Both paths work on the **same set of controller files** — compat mode does not reduce scope.

```bash
# Find all controller classes in the source project
grep -r '@RestController\|@Controller\|@ControllerAdvice' \
  <spring_source_dir>/src/main/java --include="*.java"
```

Cross-reference with the controllers list in `migration-spec.yaml`. If any controller files are
missing from the spec, add them before proceeding.

For each controller file:
- Copy it from `<spring_source_dir>` to `<quarkus_target_dir>` preserving the package structure
  (if not already copied by an earlier phase step)
- All subsequent steps operate on these files in the **target** directory

---

## Step 2 — Verify `quarkus-spring-web` extension

Confirm `quarkus-spring-web` is present in `pom.xml`. Add it if missing:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-spring-web</artifactId>
</dependency>
```

---

## Step 3 — Detect plain `@Controller` classes

`quarkus-spring-web` does **NOT** bridge plain `@Controller` — they will silently not work at runtime.

```bash
grep -r '@Controller' <quarkus_target_dir>/src/main/java --include="*.java" | grep -v '@RestController'
```

For each found class, choose one:
- If it returns JSON data → annotate with `@RestController` instead
- If it returns view templates → migrate to `@Path` returning `TemplateInstance` (Qute)
- Add each unresolved class as a WARNING in the migration report

---

## Step 4 — Search for and migrate `RestTemplate` / `RestClient`

These are NOT bridged and cause `ClassNotFoundException` at runtime:

```bash
grep -r 'RestTemplate\|RestClient' <quarkus_target_dir>/src/main/java --include="*.java"
```

Replace each occurrence with the JAX-RS Client equivalent:

```java
// ❌ WRONG — Spring RestClient (causes runtime ClassNotFoundException)
import org.springframework.web.client.RestClient;

RestClient restClient = RestClient.create(baseUrl);
List<Data> data = restClient.get()
    .uri("/data")
    .retrieve()
    .body(new ParameterizedTypeReference<List<Data>>() {});

// ✓ CORRECT — JAX-RS Client
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

Client client = ClientBuilder.newClient();
WebTarget target = client.target(baseUrl);
List<Data> data = target.path("data")
    .request(MediaType.APPLICATION_JSON)
    .get(new GenericType<List<Data>>() {});
```

**Key differences:**
- `RestClient.create()` → `ClientBuilder.newClient()`
- `.get().uri()` → `.path().request().get()`
- `ParameterizedTypeReference` → `GenericType`
- Add `@PreDestroy` to close the client properly

---

## Step 5 — Flag `@ExceptionHandler` / `@ControllerAdvice`

`@ControllerAdvice` with `@ExceptionHandler` is only partially supported by `quarkus-spring-web`.
Verify runtime behaviour. If issues arise, migrate to JAX-RS `@Provider ExceptionMapper`:

```java
// After (JAX-RS)
@Provider
public class OrderNotFoundExceptionMapper
    implements ExceptionMapper<OrderNotFoundException> {
    @Override
    public Response toResponse(OrderNotFoundException ex) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ErrorResponse(ex.getMessage()))
            .build();
    }
}
```

---

## Step 6 — Compile check

```bash
cd <quarkus_target_dir>
mvn compile
```

Fix any compilation errors before proceeding.

---

## Validation Gate

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java
mvn clean package -DskipTests -q

# Generate metadata for the target project only (Spring source metadata is already present)
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <quarkus_target_dir> -o <quarkus_target_dir>/migration-metadata/code-metadata.yaml

# Run compat-mode validation
java -jar target/migration-validator-1.0.0.jar validate rest \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml \
  --compat-mode
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in the target project
  3. Regenerate metadata and rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks (compat mode):** `quarkus-spring-web` in `pom.xml`, no plain `@Controller`,
no `RestTemplate`/`RestClient`, endpoint parity, Maven compile.

### Success Criteria

Spring MVC annotations are **intentionally kept**. Success means:
1. `quarkus-spring-web` extension present in `pom.xml`
2. No plain `@Controller` classes remain (only `@RestController` is bridged)
3. No `RestTemplate` or `RestClient` usage (not bridged — must be migrated to JAX-RS Client)
4. All original endpoints still present and reachable (endpoint parity with source)
5. Code compiles without errors

### Blocking Criteria — the following block progression to Phase 8B

- `quarkus-spring-web` extension missing from `pom.xml`
- Plain `@Controller` classes present (not bridged by `quarkus-spring-web`)
- `RestTemplate` or `RestClient` usage present (causes `ClassNotFoundException` at runtime)
- Endpoint count mismatches (missing endpoints)
- Maven compile failure

### Non-Blocking Warnings

Document these; they can be addressed later:
- `@ControllerAdvice` / `@ExceptionHandler` usage (partially supported — verify runtime behaviour)
- `HandlerInterceptor` / `WebMvcConfigurer` usage (not bridged — migrate manually if needed)
- `@CrossOrigin` usage (not processed — configure CORS via `quarkus.http.cors.*` in `application.properties`)
- `@InitBinder` / `@ModelAttribute` / `@SessionAttributes` usage (not processed — migrate manually to JAX-RS equivalents)
- Reactive return types (`Mono`, `Flux`) — not supported; migrate to `Uni`/`Multi` (Mutiny)
- Multiple `@RestControllerAdvice` classes — **hard limit**: only the first one discovered is registered; extras are silently ignored at runtime. Consolidate into a single class or migrate extra handlers to `@Provider ExceptionMapper`
- Path pattern changes (verify intentional)

**⚠️ Do not proceed to Phase 8B until validation gate passes!**

---

## Output

**Directory setup:**
```bash
mkdir -p <quarkus_target_dir>/migration-reports
```

Write `<quarkus_target_dir>/migration-reports/phase-08-web-migration.json`:

```json
{
  "phase": "web-layer-migration",
  "status": "completed",
  "strategy": "spring-web-compat",
  "controllers_migrated": 8,
  "endpoints_migrated": 45,
  "plain_controllers_resolved": 1,
  "rest_clients_migrated": 2,
  "files": [
    {
      "source": "src/main/java/com/example/controller/OrderController.java",
      "target": "src/main/java/com/example/controller/OrderController.java",
      "endpoints": 5,
      "status": "DONE"
    }
  ],
  "package_status": "PASS",
  "warnings": [],
  "manual_review": []
}
```

Then update `migration-spec.yaml` transformations.web-migration:

```yaml
transformations:
  web-migration:
    - source: src/main/java/com/example/controller/OrderController.java
      target: src/main/java/com/example/controller/OrderController.java
      technology: "spring-web-compat (quarkus-spring-web bridge)"
      status: DONE
      notes: "5 endpoints kept as-is; RestTemplate migrated to JAX-RS Client"
```

Then update `migration-metadata/migration-context.json`:
- Add `"8-web"` to `completedPhases`
- Set `phaseReports["8-web"]` to `"migration-reports/phase-08-web-migration.json"`

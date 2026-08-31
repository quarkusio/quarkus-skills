---
name: web-layer-full-migration-agent
description: Phase 8 Web Layer Migration — Full Migration. Converts Spring MVC @RestController and
  @Controller to Quarkus JAX-RS @Path resources. Migrates HTTP method annotations, parameters,
  response types, and REST clients. Called from 08-web-layer-migration.prompt.md for quarkus-rest
  or resteasy-classic strategy.
license: Apache-2.0
metadata:
  phase: 8
  agent_type: migration
---

# Phase 8 — Web Layer Migration: Full Migration (quarkus-rest / resteasy-classic)

> **Entry point:** This file is invoked by `08-web-layer-migration.prompt.md` when
> `migration_strategy.rest_framework` is `quarkus-rest`, `resteasy-classic`, or any value other
> than `spring-web-compat`.
> Output file location and inputs are defined in the entry-point file — read those first.

> **Execution mode:** This module applies deterministic annotation and import transformations and
> contains no user decision gates. It reads `execution.mode` from `migration-spec.yaml` only for
> error handling: on a compile failure it delegates to
> [`modules/testing/compile-fix.md`](../testing/compile-fix.md) (3 retries per file); if still
> failing, **`interactive`** pauses and asks the user, **`autonomous`** records the failure under
> `unresolved_issues:` and continues — no prompts in either path.

## Overview

All Spring MVC annotations are replaced with their JAX-RS / Quarkus equivalents. No compat bridge
extension is used.

Apply rules from `transformation_rules.md` RULE GROUP 4 — Web Layer Migration.

---

## Key Transformations

### 1. `@RestController` → `@Path`

```java
// Before
@RestController
@RequestMapping("/api/orders")
public class OrderController {}

// After
@Path("/api/orders")
public class OrderResource {}
```

### 2. HTTP Method Annotations

```java
// Before
@GetMapping("/{id}")
public Order getOrder(@PathVariable Long id) {}

// After
@GET
@Path("/{id}")
public Order getOrder(@PathParam("id") Long id) {}
```

| Spring | JAX-RS |
|---|---|
| `@GetMapping` | `@GET` |
| `@PostMapping` | `@POST` |
| `@PutMapping` | `@PUT` |
| `@DeleteMapping` | `@DELETE` |
| `@PatchMapping` | `@PATCH` |
| `@RequestMapping` (class-level) | `@Path` |

### 3. Parameter Annotations

| Spring | JAX-RS |
|---|---|
| `@PathVariable` | `@PathParam("name")` |
| `@RequestParam` | `@QueryParam("name")` |
| `@RequestParam Map<String, String>` | `@Context UriInfo` → `uriInfo.getQueryParameters()` |
| `@RequestBody` | *(remove annotation — implicit in JAX-RS)* |
| `@RequestHeader` | `@HeaderParam("name")` |
| `@CookieValue` | `@CookieParam("name")` |
| `@MatrixVariable` | `@MatrixParam("name")` |
| `@ResponseBody` | *(remove — implicit in JAX-RS resources)* |
| `@ModelAttribute` | `@BeanParam` + `@Consumes(MediaType.APPLICATION_FORM_URLENCODED)` |

### 4. Response Types

```java
// Before
public ResponseEntity<Order> create(@RequestBody Order order) {
    return ResponseEntity.ok(order);
}

// After
public Response create(Order order) {
    return Response.ok(order).build();
}
```

**`@ResponseStatus`** — two patterns:

```java
// Before — on a method
@GetMapping("/{id}")
@ResponseStatus(HttpStatus.ACCEPTED)
public Order get(@PathVariable Long id) {}

// After — return Response with explicit status
@GET
@Path("/{id}")
public Response get(@PathParam("id") Long id) {
    return Response.status(Response.Status.ACCEPTED).entity(order).build();
}

// Before — on a custom exception class
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {}

// After — @ServerExceptionMapper
@ServerExceptionMapper
public Response handle(OrderNotFoundException ex) {
    return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorResponse(ex.getMessage())).build();
}
```

### 5. Exception Handling (`@ControllerAdvice` → `@Provider ExceptionMapper`)

```java
// Before
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }
}

// After
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

### 6. REST Client Migration (CRITICAL)

**⚠️ MANDATORY: Replace Spring RestClient/RestTemplate with JAX-RS Client**

Spring REST client classes are NOT available in Quarkus and MUST be migrated:

```java
// ❌ WRONG — Spring RestClient (causes runtime ClassNotFoundException)
import org.springframework.web.client.RestClient;

@ApplicationScoped
public class ExternalService {
    private RestClient restClient;

    @PostConstruct
    public void init() {
        restClient = RestClient.create(baseUrl);
    }

    public List<Data> fetchData() {
        return restClient.get()
            .uri("/data")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Data>>() {});
    }
}

// ✓ CORRECT — JAX-RS Client
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
public class ExternalService {
    private Client client;
    private WebTarget target;

    @PostConstruct
    public void init() {
        client = ClientBuilder.newClient();
        target = client.target(baseUrl);
    }

    public List<Data> fetchData() {
        return target.path("data")
            .request(MediaType.APPLICATION_JSON)
            .get(new GenericType<List<Data>>() {});
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
    }
}
```

**Key differences:**
- `RestClient.create()` → `ClientBuilder.newClient()`
- `.get().uri()` → `.path().request().get()`
- `ParameterizedTypeReference` → `GenericType`
- Add `@PreDestroy` to close the client properly

---

## Import Mappings

```java
// Remove
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

// Add
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
```

---

## Special Cases

### 1. `@Controller` (Template-Based)

Convert the Java controller to a JAX-RS resource exactly like `@RestController`, but return
`TemplateInstance` instead of a data object:

```java
// Before (Spring MVC)
@Controller
public class WelcomeController {
    @GetMapping("/")
    public String welcome(Model model) {
        model.addAttribute("message", "Welcome");
        return "welcome";  // view name
    }

    @PostMapping("/todos")
    public String create(@ModelAttribute Todo todo) {
        todoService.save(todo);
        return "redirect:/todos";
    }
}

// After (Quarkus) — @Inject Template variant
@Path("/")
public class WelcomeResource {
    @Inject
    Template welcome;  // matches welcome.html in templates/

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        return welcome.data("message", "Welcome");
    }
}
```

Alternatively, use `@CheckedTemplate` for type-safe template binding (preferred for production):

```java
// After (Quarkus) — @CheckedTemplate variant
@Path("/")
public class WelcomeResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance welcome(String message);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        return Templates.welcome("Welcome");
    }
}
```

When using `@CheckedTemplate`, template files must be located at
`src/main/resources/templates/<ResourceClassName>/<methodName>.html`.

**`@ModelAttribute` → `@BeanParam`** — for form POST handlers, replace Spring's `@ModelAttribute`
with `@BeanParam` and declare `@Consumes(MediaType.APPLICATION_FORM_URLENCODED)`:

```java
// Before
@PostMapping("/todos")
public String create(@ModelAttribute Todo todo) {
    todoService.save(todo);
    return "redirect:/todos";
}

// After
@POST
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Transactional
public Response create(@BeanParam Todo todo) {
    todoService.save(todo);
    return Response.seeOther(URI.create("/todos")).build();
}
```

**Redirects** — replace Spring's `return "redirect:/path"` with `Response.seeOther()`:

```java
// Before
return "redirect:/todos";

// After
return Response.seeOther(URI.create("/todos")).build();
```

**Qute strict data map** — unlike Spring MVC's `Model`, Qute throws `TemplateException` at
runtime if a key referenced in the template is missing from the data map. Every `.data()` or
`@CheckedTemplate` call site must provide the **complete set of keys**, including on empty-result
paths. Start migration with strict rendering disabled, fix all missing variables, then re-enable:

```properties
# During migration
quarkus.qute.strict-rendering=false
quarkus.qute.property-not-found-strategy=output-original
# After all variables are confirmed present, switch to:
# quarkus.qute.strict-rendering=true
```

For templates in subdirectories, use `@io.quarkus.qute.Location("subdir/templateName")` on the
injected `Template` field.

**Template file migration** (syntax conversion, file locations, static assets, CSRF removal) is
handled by **Phase 8B** — do not duplicate that work here.

### 2. File Uploads

```java
// Before
@PostMapping("/upload")
public String upload(@RequestParam("file") MultipartFile file) {}

// After
@POST
@Path("/upload")
@Consumes(MediaType.MULTIPART_FORM_DATA)
public String upload(@MultipartForm FileUpload file) {}
```

### 3. Async Endpoints

```java
// Before
@GetMapping("/async")
public CompletableFuture<Order> getAsync() {}

// After
@GET
@Path("/async")
public Uni<Order> getAsync() {}
```

---

## Steps

### Step 1 — Get controller list from `migration-spec.yaml`

Read the controllers list from the spec. If the list is incomplete, also run:

```bash
grep -r '@RestController\|@Controller\|@ControllerAdvice' \
  <spring_source_dir>/src/main/java --include="*.java"
```

Cross-reference with the spec and add any missing files before proceeding.

### Step 2 — Migrate each controller file

For each controller file:
1. Read the source file
2. Create target file path (keep same package structure; rename `*Controller` → `*Resource` as appropriate)
3. Apply all Key Transformations above
4. Update imports (remove Spring, add JAX-RS)
5. Write transformed file to target
6. Record in transformation ledger

### Step 3 — Handle `@ControllerAdvice`

For each `@ControllerAdvice` class, create a separate `@Provider ExceptionMapper` class per
exception type (see Key Transformation #5).

### Step 4 — Search for and migrate Spring REST clients

```bash
grep -r 'RestClient\|RestTemplate' <spring_source_dir>/src/main/java --include="*.java"
```

Replace each with the JAX-RS Client equivalent (see Key Transformation #6).

### Step 5 — Verify no Spring web dependencies remain

```bash
grep -i 'springframework.*web' <quarkus_target_dir>/pom.xml
```

Remove any temporary Spring web dependencies.

### Step 6 — Compile

```bash
cd <quarkus_target_dir>
mvn clean package -DskipTests
```

Fix any compilation errors before proceeding.

---

## Error Handling

On compile errors, delegate immediately to [`modules/testing/compile-fix.md`](../testing/compile-fix.md)
with the error context. The compile-fix agent applies up to **3 retries per file**
(`approval_policy.max_compile_fix_retries_per_file`) and then acts according to
`execution.mode`:

- **`interactive`** — asks the user whether to continue with the remaining files or stop.
- **`autonomous`** — records the unfixable file under `unresolved_issues:` in
  `migration-spec.yaml` (phase `8-web`, severity ERROR) and continues automatically.

After the compile-fix agent returns, continue with the remaining controller files.
Report all issues (fixed and unresolved) in the migration report.

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
java -jar target/migration-validator-1.0.0.jar validate rest \
  <spring_source_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir>/migration-metadata/code-metadata.yaml \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

**VALIDATION LOOP (MANDATORY — DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in REST resources
  3. Regenerate metadata and rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks:** `@RestController`→`@Path`, HTTP method mappings, parameter annotations
(`@PathVariable`→`@PathParam`, `@RequestParam`→`@QueryParam`), `@RequestBody`→entity param,
response types.

### Success Criteria

1. All `@RestController` classes converted to `@Path`
2. All HTTP method annotations converted (`@GetMapping` → `@GET`, etc.)
3. All parameter annotations converted (`@PathVariable` → `@PathParam`, `@RequestParam` → `@QueryParam`)
4. Spring web imports removed, JAX-RS imports added
5. Code compiles without errors

### Blocking Criteria — the following block progression to Phase 8B

- Controllers not migrated (`@RestController` not converted to `@Path`)
- HTTP method annotations not converted (`@GetMapping` not converted to `@GET`)
- Parameter annotations not converted (`@PathVariable` not converted to `@PathParam`)
- Endpoint count mismatches (missing endpoints)
- Spring web imports still present in migrated files

### Non-Blocking Warnings

Document these; they can be addressed later:
- Path pattern changes (verify intentional)
- Response type differences (verify intentional)
- Missing exception handlers (can be added later)

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
  "strategy": "quarkus-rest",
  "controllers_migrated": 8,
  "endpoints_migrated": 45,
  "exception_handlers_migrated": 3,
  "files": [
    {
      "source": "src/main/java/com/example/controller/OrderController.java",
      "target": "src/main/java/com/example/resource/OrderResource.java",
      "endpoints": 5,
      "status": "DONE"
    }
  ],
  "package_status": "PASS",
  "warnings": [],
  "manual_review": []
}
```

Then update `migration-metadata/migration-context.json`:
- Add `"8-web"` to `completedPhases`
- Set `phaseReports["8-web"]` to `"migration-reports/phase-08-web-migration.json"`

Then update `migration-spec.yaml` transformations.web-migration:

```yaml
transformations:
  web-migration:
    - source: src/main/java/com/example/controller/OrderController.java
      target: src/main/java/com/example/resource/OrderResource.java
      technology: "@RestController → @Path + JAX-RS annotations"
      status: DONE
      notes: "5 endpoints migrated"
```

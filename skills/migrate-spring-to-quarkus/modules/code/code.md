# Module: Code

Migrate all Java source code from Spring patterns to Quarkus equivalents.

Load [references/annotation-map.md](../../references/annotation-map.md) before starting. It contains the complete annotation mapping tables for DI, REST, Data, Security, Cache, Scheduling, and Lifecycle.

## What to do

- [ ] Migrate entities (JPA → Panache if full Quarkus strategy)
- [ ] Migrate repositories (Spring Data → Panache if full Quarkus strategy)
- [ ] Simplify service layer (remove unnecessary interface+impl)
- [ ] Migrate controllers/resources (Spring MVC → JAX-RS if full Quarkus strategy)
- [ ] Migrate DI annotations (`@Autowired` → `@Inject`, `@Component` → `@ApplicationScoped`, etc.)
- [ ] Migrate `Model.addAttribute()` → Qute `Template.data()` or `@CheckedTemplate`
- [ ] Migrate `return "redirect:..."` → `Response.seeOther()`
- [ ] Remove `@SpringBootApplication` main class
- [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

Use the annotation-map.md reference for the full mapping. Below are the key patterns with before/after examples.

## Entity Layer (Full Quarkus strategy)

```java
// BEFORE: Spring Data JPA
@Entity
public class Todo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private boolean completed;
    // getters + setters
}

// AFTER: Panache Active Record
@Entity
public class Todo extends PanacheEntity {
    public String title;
    public boolean completed;
    // id provided by PanacheEntity — remove @Id and @GeneratedValue
    // public fields — remove getters/setters
}
```

## Repository Layer (Full Quarkus strategy)

Two patterns available — choose based on project conventions:

**Active Record** (queries on the entity):
```java
// Static methods on the entity
public static List<Todo> findByCompleted(boolean completed) {
    return list("completed", completed);
}
// Usage: Todo.listAll(), Todo.findById(id), Todo.findByCompleted(true)
```

**Repository class** (separate from entity):
```java
@ApplicationScoped
public class TodoRepository implements PanacheRepository<Todo> {
    public List<Todo> findByCompleted(boolean completed) {
        return list("completed", completed);
    }
}
```

**Pagination** (replaces Spring's `Page<T>` + `Pageable`):
```java
PanacheQuery<Todo> query = find("completed", completed);
query.page(Page.of(page, size));
long totalCount = query.count();
List<Todo> items = query.list();
```

**Spring compat strategy**: Keep `JpaRepository`/`CrudRepository` — they work with `quarkus-spring-data-jpa`.

## Service Layer

Spring services often use interface + implementation unnecessarily. Simplify:

```java
// BEFORE: Spring — interface + impl
public interface TodoService { List<Todo> findAll(); }

@Service
public class TodoServiceImpl implements TodoService {
    @Autowired private TodoRepository repository;
    @Override public List<Todo> findAll() { return repository.findAll(); }
}

// AFTER: Quarkus — single class
@ApplicationScoped
public class TodoService {
    @Inject TodoRepository repository;
    public List<Todo> findAll() { return repository.listAll(); }
}
```

**Decision guide:**
- Service only delegates to repository → eliminate it, inject repository directly in the resource
- Service has real business logic → keep as `@ApplicationScoped`, remove the interface
- Interface used for testing/mocking → not needed, `@InjectMock` works on concrete classes

**Spring compat strategy**: `@Service` is supported by `quarkus-spring-di` — no changes needed.

## Controller → Resource (Full Quarkus strategy)

```java
// BEFORE: Spring MVC
@Controller
public class TodoController {
    @GetMapping("/todos")
    public String list(Model model) {
        model.addAttribute("todos", todoService.findAll());
        return "todos";
    }

    @PostMapping("/todos")
    public String create(@ModelAttribute Todo todo) {
        todoService.save(todo);
        return "redirect:/todos";
    }
}

// AFTER: Quarkus + JAX-RS + Qute
@Path("/todos")
@ApplicationScoped
public class TodoResource {
    @Inject TodoService todoService;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance todos(List<Todo> todos);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        return Templates.todos(todoService.findAll());
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(@BeanParam Todo todo) {
        todoService.save(todo);
        return Response.seeOther(URI.create("/todos")).build();
    }
}
```

**Qute strict data map** — unlike Thymeleaf, Qute throws `TemplateException` if a key referenced in the template is missing from the data map. Every `.data()` call site must provide the **same complete set of keys**, including on empty-result paths.

**Spring compat strategy**: `@RestController` works with `quarkus-spring-web` (but NOT plain `@Controller`).

## Main Class Removal

If the main class **only** contains `SpringApplication.run(...)`, delete it — Quarkus auto-generates a main class.

If it contains additional logic, migrate before deleting:

- `@Bean` methods → move to an `@ApplicationScoped` class with `@Produces`
- `CommandLineRunner` → `@QuarkusMain` with `QuarkusApplication`, or `@TopCommand` (Picocli) if it parses CLI arguments
- `ApplicationRunner` → `@QuarkusMain` implementing `QuarkusApplication`
- `@EnableScheduling`, `@EnableCaching`, etc. → not needed, Quarkus enables these via extensions

### @Bean methods

```java
// BEFORE: Spring — @Bean in main class
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}

// AFTER: Quarkus — @Produces in a dedicated class
@ApplicationScoped
public class AppConfig {
    @Produces
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
```

### CommandLineRunner → @QuarkusMain

Use `@QuarkusMain` when the runner executes startup logic without parsing CLI arguments.

```java
// BEFORE: Spring — CommandLineRunner
@SpringBootApplication
public class MyApp implements CommandLineRunner {
    @Autowired DataLoader dataLoader;

    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }

    @Override
    public void run(String... args) {
        dataLoader.seed();
    }
}

// AFTER: Quarkus — @QuarkusMain
@QuarkusMain
public class MyApp implements QuarkusApplication {
    @Inject DataLoader dataLoader;

    @Override
    public int run(String... args) {
        dataLoader.seed();
        Quarkus.waitForExit();
        return 0;
    }
}
```

### CommandLineRunner with CLI arguments → @TopCommand (Picocli)

Use `@TopCommand` when the runner parses command-line arguments.

```java
// BEFORE: Spring — CommandLineRunner parsing args
@SpringBootApplication
public class MyCli implements CommandLineRunner {
    public static void main(String[] args) { SpringApplication.run(MyCli.class, args); }

    @Override
    public void run(String... args) {
        String file = args[0];
        process(file);
    }
}

// AFTER: Quarkus — Picocli @TopCommand
@TopCommand
@CommandLine.Command(name = "mycli", mixinStandardHelpOptions = true)
public class MyCli implements Runnable {
    @CommandLine.Parameters(index = "0", description = "File to process")
    String file;

    @Override
    public void run() {
        process(file);
    }
}
// Requires: quarkus-picocli extension
```

### ApplicationRunner → @QuarkusMain

```java
// BEFORE: Spring — ApplicationRunner
@SpringBootApplication
public class MyApp implements ApplicationRunner {
    @Autowired MigrationService migrations;

    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("migrate")) {
            migrations.execute();
        }
    }
}

// AFTER: Quarkus — @QuarkusMain
@QuarkusMain
public class MyApp implements QuarkusApplication {
    @Inject MigrationService migrations;

    @Override
    public int run(String... args) {
        List<String> argList = List.of(args);
        if (argList.contains("--migrate")) {
            migrations.execute();
        }
        Quarkus.waitForExit();
        return 0;
    }
}
```

## Watch out

- **Missing `@Transactional`**: Quarkus uses `jakarta.transaction.Transactional`, not Spring's
- **Bean discovery**: Quarkus uses build-time CDI; beans must have a scope annotation
- **No OSIV**: Quarkus doesn't have Open Session in View; lazy loading outside transactions will fail
- **No component scanning**: Beans in external JARs need a Jandex index or `quarkus.index-dependency`
- **JAX-RS path conflicts**: Spring allows overlapping `@RequestMapping` paths — JAX-RS does not. Check for duplicate `@Path` values

## Spring Compat Extension Limitations

When using the compatibility strategy (`quarkus-spring-*` extensions), be aware of these **verified limitations from the Quarkus source code**:

| Extension | What does NOT work |
|---|---|
| `quarkus-spring-di` | `@Primary`, `@Conditional*`, `@Profile`, `@Lazy` not processed. SpEL `#{...}` in `@Value` throws error. `@Bean` must be inside `@Configuration` class. |
| `quarkus-spring-web` | Only `@RestController` — plain `@Controller` not supported. Only one `@RestControllerAdvice` per app. `@CrossOrigin`, `@InitBinder`, `@ModelAttribute` not supported. No reactive types (`Mono`, `Flux`). |
| `quarkus-spring-security` | Limited SpEL in `@PreAuthorize`: only `hasRole`, `hasAnyRole`, `permitAll`, `denyAll`, `isAuthenticated`, `@bean.method()`, param comparison. Cannot mix `and`/`or` operators. Cannot combine `@Secured` with `@PreAuthorize`. |
| `quarkus-spring-data-jpa` | SpEL `#{...}` in `@Query` not supported. No `Distinct` queries. Limited custom repository fragment support. |
| `quarkus-spring-cache` | Single cache name only (no arrays). `key`, `condition`, `unless`, `keyGenerator`, `cacheManager` parameters NOT supported. No `@Caching` or `@CacheConfig`. |
| `quarkus-spring-scheduled` | `fixedDelay` NOT supported (only `fixedRate`). Cannot combine `initialDelay` with `cron`. |

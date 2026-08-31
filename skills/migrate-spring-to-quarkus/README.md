# Spring to Quarkus Migration Framework

A comprehensive, multi-agent framework for migrating Spring Framework and Spring Boot applications to Quarkus with automated validation and verification.

## Overview

This framework provides a structured, phase-by-phase approach to migrating Spring Framework and Spring Boot applications to Quarkus. It uses specialized agents for different aspects of the migration, ensuring reliability and traceability.

### 🎨 Execution Modes: Interactive (default) or Autonomous

The skill runs in one of two **execution modes**:

- **Interactive (default)** — designed for IDE-based development (VS Code, IntelliJ, etc.) with active
  developer involvement. You remain in control, making key technology decisions at critical points, and
  each phase requires explicit approval (`yes`) before proceeding so you can review changes as they happen.
- **Autonomous (opt-in)** — the skill runs end-to-end without approval stops. It chooses best-fit
  technologies from the discovered codebase for any decision you didn't pin, and when it hits a problem it
  can't fix, it documents the issue and continues rather than pausing — every unresolved item is surfaced
  in the final Migration Report.

Two independent controls select behavior (see [Configuration](#configuration)):

- **`mode`** — `interactive` | `autonomous` (the execution axis above).
- **`strategy`** — `full-migration` (rewrite Spring annotations to CDI/JAX-RS/Panache) |
  `spring-compatibility` (keep Spring annotations via Quarkus extension bridges).

**Interactive-mode characteristics:**
- **Developer-Guided Migration** - You make key technology decisions at critical points
- **Phase-by-Phase Approval** - Each phase requires explicit approval (`yes`) before proceeding
- **Technology Decision Points** - The framework presents options for persistence, messaging, database, and more
- **Real-Time Review** - Examine generated code and validation reports before moving forward
- **Interactive Problem Solving** - Fix automatically, manually, or skip and document

In interactive mode this is **not** a hands-off tool — it's an assistant that keeps you involved in
important decisions. Autonomous mode trades that involvement for an unattended run; use it when you've
already decided on a strategy (or trust the agent to pick one) and want the migration to complete without
prompts.

**Supports:**
- Spring Framework (standalone applications)
- Spring Boot applications (all versions)
- Spring MVC and Spring WebFlux
- Spring Data JPA
- Spring Kafka, RabbitMQ, JMS
- Spring Security

## Architecture

The framework is based on a **multi-agent architecture** where each agent handles a specific migration concern:

1. **Discovery Agent** - Scans Spring Boot project and identifies technologies
2. **Dependency Analysis Agent** - Analyzes build configuration and dependencies
3. **Migration Planning Agent** - Creates comprehensive migration plan
4. **Project Bootstrap Agent** - Sets up Quarkus project structure
5. **Persistence Migration Agent** - Migrates JPA entities and Spring Data repositories
6. **Service Layer Migration Agent** - Converts Spring services to CDI beans
7. **Messaging Migration Agent** - Migrates Kafka/RabbitMQ/JMS to Reactive Messaging
8. **Web Layer Migration Agent** - Converts Spring MVC to Quarkus REST
9. **Configuration Agent** - Migrates application.properties and configuration classes
10. **Compile Fix Agent** - Automatically fixes compilation errors
11. **Validation Agent** - Validates the migrated application
12. **Reporting Agent** - Generates comprehensive migration report

## Key Features

### 🎯 Comprehensive Coverage
- **Spring Framework & Spring Boot** - Both standalone and Boot applications
- **Spring Web (MVC/WebFlux)** → Quarkus REST
- **Spring Data JPA** → Hibernate ORM with Panache
- **Spring Kafka/RabbitMQ/JMS** → SmallRye Reactive Messaging
- **Spring Security** → Quarkus Security
- **Spring Configuration** → Quarkus Configuration
- **XML & Annotation-based** configuration

### 🔍 Automated Validation
- Validates controller/endpoint migration
- Verifies service layer transformation
- Checks repository migration
- Validates configuration properties
- Ensures compilation success

### 📊 Detailed Reporting
- Phase-by-phase progress tracking
- Transformation ledger for all changes
- Issue tracking and manual review flags
- Comprehensive migration summary

### 🛡️ Safety Features
- User approval required after each phase (interactive mode)
- Automatic backup and rollback capability
- Compile error detection and auto-fix
- Manual review flagging for complex cases (interactive) / unresolved-issue documentation (autonomous)

## Directory Structure

```
bob/spring2quarkusmigration_java_validators/
├── AGENT.md                    # Main orchestrator agent
├── README.md                   # This file
├── agents/                     # Individual agent prompts
│   ├── 01-discovery.prompt.md
│   ├── 01b-dependency-analysis.prompt.md
│   ├── 02-migration-planning.prompt.md
│   ├── 03-project-bootstrap.prompt.md
│   ├── 04-database-migration.prompt.md
│   ├── 05-persistence-migration.prompt.md
│   ├── 06-service-migration.prompt.md
│   ├── 07-messaging-migration.prompt.md
│   ├── 08-web-layer-migration.prompt.md
│   ├── 08b-web-views-migration.prompt.md
│   ├── 09-configuration.prompt.md
│   ├── 10-compile-fix.prompt.md
│   ├── 11-validation.prompt.md
│   └── 12-reporting.prompt.md
├── references/                 # Reference documentation
│   ├── transformation_rules.md
│   └── FILE_ORGANIZATION.md
├── templates/                  # Migration templates
│   └── migration-spec-template.yaml
└── validators/                 # Validation tooling
    └── java/                   # Java CLI validator (migration-validator-1.0.0.jar)
        ├── pom.xml
        └── src/main/java/com/migration/validator/
            ├── cli/            # Picocli entry points (validate, extract)
            ├── extractor/      # Metadata extractors (generate, messaging, config)
            ├── model/          # Data models
            └── core/           # Shared utilities
```

## Transformation Rules

### Annotations

| Spring | Quarkus |
|--------|---------|
| `@Service` | `@ApplicationScoped` |
| `@Component` | `@ApplicationScoped` |
| `@Repository` | `@ApplicationScoped` or Panache |
| `@RestController` | `@Path` |
| `@Autowired` | `@Inject` |
| `@Value` | `@ConfigProperty` |
| `@GetMapping` | `@GET` |
| `@PostMapping` | `@POST` |
| `@PathVariable` | `@PathParam` |
| `@RequestParam` | `@QueryParam` |
| `@KafkaListener` | `@Incoming` |
| `@Async` | `Uni<T>` or `@Asynchronous` |
| `@Scheduled` | `@Scheduled` |

### Dependencies

| Spring Starter | Quarkus Extension |
|----------------|-------------------|
| `spring-boot-starter-web` | `quarkus-rest-jackson` |
| `spring-boot-starter-data-jpa` | `quarkus-hibernate-orm-panache` |
| `spring-boot-starter-security` | `quarkus-security` |
| `spring-kafka` | `quarkus-smallrye-reactive-messaging-kafka` |
| `spring-boot-starter-actuator` | `quarkus-smallrye-health` |
| `spring-boot-starter-validation` | `quarkus-hibernate-validator` |

### Configuration Properties

| Spring Property | Quarkus Property |
|-----------------|------------------|
| `server.port` | `quarkus.http.port` |
| `spring.datasource.url` | `quarkus.datasource.jdbc.url` |
| `spring.datasource.username` | `quarkus.datasource.username` |
| `spring.jpa.hibernate.ddl-auto` | `quarkus.hibernate-orm.database.generation` |
| `spring.kafka.bootstrap-servers` | `kafka.bootstrap.servers` |

## Configuration

You can configure the migration up front with a `.quarkus-migration.yml` file in your **source project
root**, or pass the same controls in the invocation prompt.

```yaml
# .quarkus-migration.yml
mode: autonomous              # interactive | autonomous   (optional; default: interactive)
strategy: full-migration      # full-migration | spring-compatibility   (optional)
```

- **`mode`** — `autonomous` runs the whole migration without approval prompts; `interactive` (default)
  stops for `yes` after each phase.
- **`strategy`** — pins the full-vs-compat approach. If omitted, it is asked (interactive) or chosen by the
  agent from the discovered code (autonomous).

When the file is present, the skill skips the corresponding prompts and uses the configured values. This is
useful for teams that have already decided on a mode/strategy and don't want to be asked every time, or for
unattended/CI-style runs.

You can also pass the controls directly when invoking the skill:

```
Migrate ./my-spring-app to Quarkus autonomously using the full migration strategy
```

**Resolution order (first match wins), applied independently to each control:**

1. Skill/prompt argument
2. `.quarkus-migration.yml` config file
3. Default — `mode` defaults to `interactive`; an unresolved `strategy` is asked (interactive) or
   agent-selected (autonomous)

## Usage

### Prerequisites

- Java 17 or higher
- Maven 3.9+ or Gradle 8+
- Python 3.12+ (for validators)

### Testing with Sample Projects

For contributors looking to test and experiment with the migration framework, we recommend using the [scarfbench benchmark](https://github.com/scarfbench/benchmark) repository. It provides a collection of real-world applications that can be used to validate and improve the migration process.

```bash
# Clone the scarfbench benchmark repository
git clone https://github.com/scarfbench/benchmark.git

# Use any of the sample projects as a source for migration testing
cd benchmark
# Explore available Spring Boot projects and select one for migration
```

### Running a Migration

1. **Prepare the target directory:**
   ```bash
   # Create a new folder for the migrated Quarkus project
   mkdir -p <target-migration-folder>
   
   # Example:
   mkdir -p ~/projects/my-app-quarkus
   ```
   
   **Note:** It's recommended to create a separate folder for the target migration to keep the original Spring project intact. This allows for easy comparison and rollback if needed.

2. **Start the orchestrator:**
   ```
   Say: Migrate <path-to-spring-project> to <target-migration-folder>
   ```

3. **Follow the process:**
   - **Interactive mode:** review each phase output, approve with `yes` to proceed, use `show-details` for
     more information, or `no` to fix issues before proceeding.
   - **Autonomous mode:** the skill runs all phases without prompting; review the final Migration Report,
     including any items recorded under `unresolved_issues`.

3. **Monitor progress:**
   - Check `migration-context.json` for current state
   - Review `migration-spec.yaml` for the plan
   - Examine phase reports in the workspace

## Java Validators

### Available Validators

**1. ProjectSetupValidator** (Phase 3)
```bash
cd validators/java
mvn clean package -DskipTests -q
java -jar target/migration-validator-1.0.0.jar validate project-setup \
  <target_project_root> \
  <migration-spec.yaml>
```

**2. DatabaseMigrationValidator** (Phase 4)
```bash
java -jar target/migration-validator-1.0.0.jar validate database \
  <target_project_root> \
  <migration-spec.yaml>
```

**3. PersistenceValidator** (Phase 5)
```bash
# Build validator if needed
cd validators/java && mvn clean package -DskipTests -q

# Generate metadata first
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <spring_source_dir> -o <spring_source_dir>/code-metadata.yaml
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <quarkus_target_dir> -o <quarkus_target_dir>/code-metadata.yaml

# Run validator
java -jar target/migration-validator-1.0.0.jar validate persistence \
  <spring_source_dir>/code-metadata.yaml \
  <quarkus_target_dir>/code-metadata.yaml \
  <migration-spec.yaml>
```

**4. ServiceValidator** (Phase 6)
```bash
java -jar target/migration-validator-1.0.0.jar validate services \
  <spring_source_dir> \
  <quarkus_target_dir> \
  <migration-spec.yaml>
```

**5. MessagingValidator** (Phase 7)
```bash
# Build validator if needed
cd validators/java && mvn clean package -DskipTests -q

# Extract messaging metadata first
java -jar target/migration-validator-1.0.0.jar extract spring-messaging \
  <spring_source_dir> <spring_source_dir>/spring-messaging-metadata.json
java -jar target/migration-validator-1.0.0.jar extract quarkus-messaging \
  <quarkus_target_dir> <quarkus_target_dir>/quarkus-messaging-metadata.json

# Run validator
java -jar target/migration-validator-1.0.0.jar validate messaging \
  <spring_source_dir>/spring-messaging-metadata.json \
  <quarkus_target_dir>/quarkus-messaging-metadata.json \
  <migration-spec.yaml>
```

**6. RestValidator** (Phase 8)
```bash
# Build validator if needed
cd validators/java && mvn clean package -DskipTests -q

# Generate metadata first (same as PersistenceValidator)
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <spring_source_dir> -o <spring_source_dir>/code-metadata.yaml
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <quarkus_target_dir> -o <quarkus_target_dir>/code-metadata.yaml

# Run validator
java -jar target/migration-validator-1.0.0.jar validate rest \
  <spring_source_dir>/code-metadata.yaml \
  <quarkus_target_dir>/code-metadata.yaml \
  <migration-spec.yaml>
```

**7. ConfigValidator** (Phase 9)
```bash
# Build validator if needed
cd validators/java && mvn clean package -DskipTests -q

# Extract configuration metadata first
java -jar target/migration-validator-1.0.0.jar extract server-config \
  <spring_source_dir> <spring_source_dir>/spring-config-metadata.json
java -jar target/migration-validator-1.0.0.jar extract server-config \
  <quarkus_target_dir> <quarkus_target_dir>/quarkus-config-metadata.json

# Run validator
java -jar target/migration-validator-1.0.0.jar validate config \
  <spring_source_dir>/spring-config-metadata.json \
  <quarkus_target_dir>/quarkus-config-metadata.json \
  <migration-spec.yaml>
```

### Validator Features

- ✅ **Exit Codes**: 0 for success, 1 for failures (CI/CD friendly)
- ✅ **Automatic Updates**: Validators update migration-spec.yaml automatically
- ✅ **Backup System**: Creates timestamped backups before updates
- ✅ **Detailed Reports**: Comprehensive validation results with error details
- ✅ **Metadata-Driven**: Uses CLDK for accurate code analysis

### Java Metadata Extractors

- `extract metadata` - Code metadata extraction
- `extract spring-messaging` - Spring JMS metadata extraction
- `extract quarkus-messaging` - Quarkus messaging metadata extraction
- `extract server-config` - Configuration property extraction

## Migration Phases

### Phase 0: Environment Preparation
- Verify Java and Maven versions
- Kill running processes
- Initialize migration context

### Phase 1: Discovery
- Scan Spring Boot project
- Detect technologies and patterns
- Generate repository metadata

### Phase 2: Migration Planning
- Create comprehensive migration plan
- Resolve technology decisions (ask the user in interactive mode; auto-select best fit in autonomous mode)
- Generate migration-spec.yaml

### Phase 3: Project Bootstrap
- Create Quarkus project structure
- Add required extensions
- Set up configuration files

### Phase 5: Persistence Migration
- Migrate JPA entities
- Convert Spring Data repositories
- Update transaction annotations

### Phase 6: Service Layer Migration
- Convert @Service to @ApplicationScoped
- Replace @Autowired with @Inject
- Handle async methods

### Phase 7: Messaging Migration
- Convert Kafka/RabbitMQ listeners
- Migrate message producers
- Configure reactive messaging

### Phase 8: Web Layer Migration
- Convert @RestController to @Path
- Migrate HTTP method annotations
- Update parameter annotations

### Phase 9: Configuration Migration
- Convert application.properties
- Migrate @Configuration classes
- Create lifecycle hooks

### Phase 11: Validation
- Compile and package
- Run smoke tests
- Validate all transformations

### Final: Reporting
- Generate migration summary
- Document manual review items
- Provide next steps

## Key Files

### migration-context.json
Tracks migration progress and state. Contains:
- Current phase
- Completed phases (user-approved phases are those in this list)
- Paths to key artifacts

### migration-spec.yaml
The binding contract between all agents. Contains:
- Project metadata
- Detected features
- Migration strategy
- Transformation ledger
- Architectural decisions

### repo-metadata.json
Source project analysis. Contains:
- Spring Boot version
- Detected technologies
- Component inventory
- Database configuration

## Best Practices

1. **Always review phase outputs** before approving
2. **Keep migration-spec.yaml** as the source of truth
3. **Document decisions** in the decisions section
4. **Flag complex cases** for manual review
5. **Run validators** after each major phase
6. **Test incrementally** as you migrate

## Validator Details

### Metadata Extractors

#### `extract metadata`
Scans a Java project and generates `code-metadata.yaml` for contract validation:
- Extracts Spring MVC controllers, Quarkus REST resources
- Extracts JPA entities and Spring Data repositories
- Outputs structured YAML consumed by `validate persistence` and `validate rest`

#### `extract spring-messaging`
Extracts Spring messaging metadata to JSON:
- @KafkaListener, @RabbitListener, @JmsListener detection
- JmsTemplate, KafkaTemplate, RabbitTemplate usage
- Message destinations (topics, queues)
- Advanced patterns: @SendTo, ReplyingKafkaTemplate, Container Factories

#### `extract quarkus-messaging`
Extracts Quarkus messaging metadata to JSON:
- @Incoming, @Outgoing consumer/producer detection
- Emitter and @Channel injections
- Reactive Messaging channels
- Advanced patterns: Uni<Message<T>>, ack/nack handlers, batch consumers

#### `extract server-config`
Extracts server configuration metadata to JSON:
- Reads `application.properties` from the project root
- Maps property keys for before/after comparison

### Validators

#### `validate project-setup`
Validates Quarkus project structure and dependencies.

#### `validate database`
Validates database migration (Flyway/Liquibase scripts, datasource config).

#### `validate persistence`
Validates JPA entity and repository migration:
- Entity field mapping
- Repository method migration
- Transaction annotation conversion

#### `validate services`
Validates service layer migration:
- @Service → @ApplicationScoped conversion
- @Autowired → @Inject migration
- Async method handling

#### `validate messaging`
Validates messaging migration:
- Kafka/JMS listener → @Incoming mapping
- Producer → @Outgoing / Emitter mapping
- Channel configuration

#### `validate rest`
Validates REST API contract:
- Endpoint coverage analysis
- Return type compatibility
- Parameter count/type validation
- Media type consistency

#### `validate config`
Validates configuration migration:
- application.properties key mapping
- @Value → @ConfigProperty transformation

#### `validate ui`
Validates UI layer migration (JSF/JSP/Thymeleaf/FreeMarker → Qute).

## Troubleshooting

### Compilation Errors
- The Compile Fix Agent will attempt automatic fixes
- Check compile-fix-report.json for details
- Manual review may be required for complex cases

### Missing Endpoints
- Run `validate rest` for contract-level analysis
- Run `validate project-setup` for structural validation
- Check transformation ledger in migration-spec.yaml
- Review web-migration-report.json

### Configuration Issues
- Run `validate config` to identify gaps
- Verify property mappings in transformation_rules.md
- Check application.properties in target project
- Review configuration-migration-report.json

### Validation Failures
- Check validator output for specific issues
- Review severity levels (ERROR vs WARNING)
- Use suggestions provided in validation reports
- Run `validate rest` for comprehensive REST analysis

### Metadata Generation Issues
- Ensure source directories contain Java files
- Check file encoding (UTF-8 expected)
- Verify regex patterns match your code style
- Review generated YAML for completeness

## Extending the Framework

### Adding New Validators
1. Create a new `@Command` class under `validators/java/src/main/java/com/migration/validator/cli/commands/`
2. Implement `Callable<Integer>` and use Picocli `@Parameters` / `@Option` annotations
3. Follow the pattern: extract → compare → report using `ValidationReport`
4. Register the command in `ValidateCommand` or `ExtractCommand`
5. Rebuild the jar: `cd validators/java && mvn clean package -DskipTests`
6. Document in README.md

### Adding New Transformation Rules
1. Document rule in `references/transformation_rules.md`
2. Update relevant agent prompt
3. Add validation for the new rule in the appropriate Java validator class

### Customizing Agents
1. Modify agent prompt in `agents/` directory
2. Test with sample project
3. Update validators to match new transformation patterns

## Related Documentation

- [transformation_rules.md](./references/transformation_rules.md) - Complete transformation rules
- [SKILL.md](./SKILL.md) - Orchestrator documentation

## Support

For issues or questions:
1. Check the `references/transformation_rules.md` for guidance
2. Examine migration-spec.yaml for current state
3. Check phase reports for detailed information

# Spring to Quarkus Migration Validators (Java)

Java-based validators for verifying Spring Boot to Quarkus migration correctness. They are packaged as a unified CLI tool with Picocli for easy command-line usage.

## Building

```bash
mvn clean package
```

This creates an executable JAR with all dependencies bundled: `target/migration-validator-1.0.0.jar`

## Usage

### General Help

```bash
# Show main help
java -jar target/migration-validator-1.0.0.jar --help

# Show validate command help
java -jar target/migration-validator-1.0.0.jar validate

# Show subcommand help
java -jar target/migration-validator-1.0.0.jar validate project-setup -h
java -jar target/migration-validator-1.0.0.jar validate ui -h
```

### Verbose Mode

Add `-v` or `--verbose` to any command for detailed logging:

```bash
java -jar target/migration-validator-1.0.0.jar validate project-setup \
  /path/to/project \
  /path/to/spec.yaml \
  --verbose
```

## Available Validators

### 1. Project-setup

Validates Quarkus project structure, dependencies, and build configuration.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate project-setup \
  <projectRoot> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate project-setup \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

**Checks**:
- ✓ pom.xml exists
- ✓ application.properties exists
- ✓ No Spring Boot files (@SpringBootApplication)
- ✓ No Spring Framework dependencies
- ✓ POM coordinates (groupId, artifactId, version)
- ✓ quarkus-maven-plugin present
- ✓ quarkus-bom in dependencyManagement (with property placeholder support)
- ✓ Quarkus extensions present
- ✓ Directory structure (src/main/java, src/main/resources, etc.)
- ✓ Maven compile succeeds

### 2. Database

Validates database configuration, import.sql, and JDBC setup.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate database \
  <projectRoot> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate database \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

### 3. persistence

Validates JPA entity migration from Spring Data to Quarkus Panache/Hibernate ORM.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate persistence \
  <springMetadata> \
  <quarkusMetadata> \
  <projectRoot> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate persistence \
  /path/to/spring-metadata.yaml \
  /path/to/quarkus-metadata.yaml \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

### 4. Services

Validates Spring @Service/@Component migration to CDI beans.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate services \
  <targetDir> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate services \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

### 5. Messaging

Validates Kafka/JMS/RabbitMQ migration to SmallRye Reactive Messaging.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate messaging \
  <springMetadata> \
  <quarkusMetadata> \
  <projectDir> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate messaging \
  /path/to/spring-metadata.yaml \
  /path/to/quarkus-metadata.yaml \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

### 6. Rest Endpoints

Validates REST endpoint migration from Spring MVC to JAX-RS.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate rest \
  <springMetadata> \
  <quarkusMetadata> \
  <projectRoot> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate rest \
  /path/to/spring-metadata.yaml \
  /path/to/quarkus-metadata.yaml \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

### 7. FrontEnd

Validates view technology migration (JSP/JSF/Thymeleaf/FreeMarker → Qute or MyFaces).

**Supports Multiple Migration Paths**:
- JSP → Qute
- Thymeleaf → Qute
- FreeMarker → Qute
- JSF → Qute
- JSF → Quarkus MyFaces (preserve JSF)

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate ui \
  <sourceDir> \
  <targetDir> \
  <migrationType> \
  <specPath>
```

**Migration Types**:
- `jsp-qute` - JSP to Qute migration
- `thymeleaf-qute` - Thymeleaf to Qute migration
- `freemarker-qute` - FreeMarker to Qute migration
- `jsf-qute` - JSF to Qute migration
- `jsf-myfaces` - JSF to Quarkus MyFaces (preserve JSF)

**Example (JSP to Qute)**:
```bash
java -jar target/migration-validator-1.0.0.jar validate ui \
  /path/to/spring/project \
  /path/to/quarkus/project \
  jsp-qute \
  /path/to/migration-spec.yaml
```

**Example (JSF to MyFaces)**:
```bash
java -jar target/migration-validator-1.0.0.jar validate ui \
  /path/to/spring/project \
  /path/to/quarkus/project \
  jsf-myfaces \
  /path/to/migration-spec.yaml
```

**Qute Migration Checks** (jsp-qute, thymeleaf-qute, freemarker-qute, jsf-qute):
- ✓ Qute dependency present (quarkus-rest-qute or quarkus-qute)
- ✓ Template structure (templates in src/main/resources/templates/)
- ✓ Static resources location (META-INF/resources/)
- ✓ No Spring Security CSRF tokens
- ✓ Qute configuration in application.properties
- ✓ Technology-specific removal (JSP files, JSTL tags, Thymeleaf attributes, etc.)
- ✓ Legacy dependencies removed
- ✓ Expression language migrated
- ✓ Source/target parity
- ✓ Quarkus build succeeds

**JSF MyFaces Checks** (jsf-myfaces):
- ✓ MyFaces Quarkus extension present
- ✓ Jakarta Faces namespaces (not javax)
- ✓ faces-config.xml uses Jakarta
- ✓ CDI @Named beans (not @ManagedBean)
- ✓ Converters/validators use managed=true
- ✓ @ViewScoped beans implement Serializable
- ✓ XHTML files in META-INF/resources
- ✓ javax.* imports migrated to jakarta.*
- ✓ Source/target parity
- ✓ Build compile succeeds

### 8. Configuration

Validates application.properties migration from Spring Boot to Quarkus.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar validate config \
  <springProject> \
  <quarkusProject> \
  <specPath>
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar validate config \
  /path/to/spring/project \
  /path/to/quarkus/project \
  /path/to/migration-spec.yaml
```

## Extract Commands

Use the `extract` command to generate metadata artifacts from Spring or Quarkus projects.

### `extract metadata`

Generates `repo_metadata.yaml` from a Java project by running CodeAnalyzer and the metadata extractors.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar extract metadata \
  <projectRoot> \
  [--output repo_metadata.yaml] \
  [--analysis-level 1]
```

**Example**:
```bash
java -jar target/migration-validator-1.0.0.jar extract metadata \
  /path/to/project \
  --output /path/to/repo_metadata.yaml
```

### `extract spring-messaging`

Extracts Spring messaging metadata to a JSON output file.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar extract spring-messaging \
  <projectRoot> \
  <outputFile>
```

### `extract quarkus-messaging`

Extracts Quarkus messaging metadata to a JSON output file.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar extract quarkus-messaging \
  <projectRoot> \
  <outputFile>
```

### `extract server-config`

Extracts server configuration metadata to a JSON output file.

**Usage**:
```bash
java -jar target/migration-validator-1.0.0.jar extract server-config \
  <projectRoot> \
  <outputFile>
```

## Dependencies

- Java 17+
- Maven 3.6+
- SnakeYAML 2.0 (for YAML parsing)
- Jackson 2.15.2 (for YAML processing)

## CLI Architecture

The validators are now packaged as a unified CLI tool using Picocli:

- **Main Entry Point**: `MigrationValidatorCLI` - Root command with version and help
- **Validate Command**: `ValidateCommand` - Main command with 8 validator subcommands
- **Extract Command**: `ExtractCommand` - Metadata extraction command with `metadata`, `spring-messaging`, `quarkus-messaging`, and `server-config` subcommands
- **Subcommands**: Commands grouped by validation and extraction use cases
- **Instance-Based Design**: Validators use constructor injection for better testability
- **Single JAR**: All dependencies bundled with Maven Shade Plugin

### Benefits

1. **Simplified Usage**: Single executable JAR, no classpath management
2. **Better UX**: Picocli provides automatic help generation and validation
3. **Testability**: Instance-based validators are easier to unit test
4. **Maintainability**: Clear separation between CLI layer and validation logic
5. **Extensibility**: Easy to add new validators as subcommands

## Known Issues

- ⚠️ CDI bean producer detection (needs implementation)
- ⚠️ API root path configuration validation (needs implementation)
- ⚠️ SQL reserved keyword detection (needs implementation)
- ⚠️ Persistence validator false positives (needs investigation)

## Contributing

When adding new validators or fixing issues:

1. Follow the existing pattern in `ProjectSetupValidator.java`
2. Use `ValidationReport` class for consistent reporting
3. Add debug output for troubleshooting
4. Update this README with usage examples
5. Add test cases for the fix

## Related Documentation

- [transformation_rules.md](../../references/transformation_rules.md) - Migration transformation rules

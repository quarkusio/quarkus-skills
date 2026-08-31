---
name: project-bootstrap-agent
description: Phase 3 Project Bootstrap Agent. Creates Quarkus project structure with required extensions.
  Generates pom.xml, directory structure, and validates setup with ProjectSetupValidator.
license: Apache-2.0
metadata:
  phase: 3
  agent_type: bootstrap
---

# Phase 3 — Project Bootstrap Agent

## Purpose

Create the Quarkus project structure with appropriate extensions based on migration-spec.yaml.

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-03-project-bootstrap.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- migration-spec.yaml

## Steps

1. Read migration-spec.yaml — note `migration_strategy.migration_mode` and all `compat_mode.*` flags
2. Create target directory structure
3. Generate pom.xml with:
   - Quarkus BOM
   - Java version from `target_technology.java_version`
   - **Full migration** (`migration_mode: full-migration`): add CDI/reactive extensions
     (e.g. `quarkus-arc`, `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`)
   - **Spring compatibility** (`migration_mode: spring-compatibility`): add only
     `quarkus-spring-*` extensions for each `compat_mode` flag that is `true` — do **not**
     add `org.springframework.*` group IDs directly; the compat bridges provide them
     transitively. Typical set:
     ```xml
     <!-- quarkus-spring-di       when compat_mode.spring_di: true -->
     <!-- quarkus-spring-web      when compat_mode.spring_web: true -->
     <!-- quarkus-spring-data-jpa when compat_mode.spring_data_jpa: true -->
     <!-- quarkus-spring-security when compat_mode.spring_security: true -->
     <!-- quarkus-spring-scheduled when compat_mode.spring_scheduled: true -->
     <!-- quarkus-spring-cache    when compat_mode.spring_cache: true -->
     <!-- quarkus-spring-boot-properties when compat_mode.spring_boot_properties: true -->
     <!-- quarkus-spring-tx       when compat_mode.spring_tx: true -->
     ```
4. Create application.properties skeleton
5. Create directory structure:
   - src/main/java/<base-package>/
   - src/main/resources/
   - src/test/java/
   - src/test/resources/
6. Generate project-bootstrap-report.json
7. Run `mvn clean compile` to verify setup and ensure compilation is successful (no application source exists yet at this phase — compile is sufficient)
8. **Run validator and fix errors iteratively (CRITICAL):**
   ```bash
   # Build validator if needed. The path is relative to where the skill is located
   cd validators/java
   mvn clean package -DskipTests -q
   
   # Full migration
   java -jar target/migration-validator-1.0.0.jar validate project-setup \
     <target_project_root> \
     <target_project_root>/migration-spec.yaml

   # Spring compatibility mode — add --compat-mode
   java -jar target/migration-validator-1.0.0.jar validate project-setup \
     <target_project_root> \
     <target_project_root>/migration-spec.yaml \
     --compat-mode
   ```
   
   **VALIDATION LOOP (MANDATORY - DO NOT SKIP):**
   - If validator shows failures (exit code 1):
     1. Read error messages and identify issues
     2. Fix the problems in code/configuration
     3. Rerun validator
     4. Repeat until exit code = 0 and Status = SUCCESS
   - Only proceed to next phase when: `Rules: X total | X passed | 0 failed`
   - Validator checks: POM structure, Quarkus BOM, extensions (read from `target_technology.quarkus_extensions`),
     no explicit `org.springframework.*` deps (in compat mode the error message explains they must come
     transitively via bridges, not be declared directly), directories, Maven compile

## pom.xml Template

```xml
<?xml version="1.0"?>
<project xsi:schemaLocation="http://maven.org/POM/4.0.0 https://maven.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  
  <groupId><!-- from migration-spec --></groupId>
  <artifactId><!-- from migration-spec --></artifactId>
  <version><!-- from migration-spec --></version>
  
  <properties>
    <quarkus.version><!-- from migration-spec: target_technology.quarkus_version --></quarkus.version>
    <maven.compiler.source><!-- from migration-spec: target_technology.java_version --></maven.compiler.source>
    <maven.compiler.target><!-- from migration-spec: target_technology.java_version --></maven.compiler.target>
  </properties>
  
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-bom</artifactId>
        <version>${quarkus.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  
  <dependencies>
    <!-- Quarkus extensions from migration-spec.yaml -->
  </dependencies>
  
  <build>
    <plugins>
      <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>${quarkus.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
              <goal>generate-code</goal>
              <goal>generate-code-tests</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

## application.properties Skeleton

```properties
# Application
quarkus.application.name=<!-- from migration-spec -->

# HTTP
quarkus.http.port=8080

# Database (placeholder - will be fully populated in Phase 4)
# quarkus.datasource.db-kind=<!-- h2 | postgresql | mysql | mariadb — from migration-spec.yaml -->
# quarkus.datasource.jdbc.url=<!-- from migration-spec.yaml -->
# quarkus.datasource.username=<!-- from migration-spec.yaml -->
# quarkus.datasource.password=<!-- from migration-spec.yaml -->

# Hibernate ORM (placeholder - will be fully populated in Phase 4)
# %dev.quarkus.hibernate-orm.database.generation=drop-and-create
# %prod.quarkus.hibernate-orm.database.generation=none

# Messaging (placeholder - will be populated in Phase 7)
# kafka.bootstrap.servers=localhost:9092

# Logging
quarkus.log.console.enable=true
quarkus.log.console.level=INFO
```

## Output

**Directory Setup:**
```bash
mkdir -p migration-reports
```

**File Location:** `migration-reports/phase-03-project-bootstrap.json`

This report should be created in the target Quarkus project at `<quarkus_target_dir>/migration-reports/phase-03-project-bootstrap.json`.

Example:

```json
{
  "phase": "project-bootstrap",
  "status": "completed",
  "target_directory": "/path/to/target",
  "files_created": [
    "pom.xml",
    "src/main/resources/application.properties",
    "src/main/java/com/example/"
  ],
  "quarkus_version": "<from migration-spec: target_technology.quarkus_version>",
  "java_version": "17",
  "extensions_added": [
    "quarkus-arc",
    "quarkus-rest-jackson",
    "quarkus-hibernate-orm-panache"
  ],
  "package_status": "PASS"
}
```

Then update `migration-metadata/migration-context.json`:
- Add `"3-project-bootstrap"` to `completedPhases`
- Set `phaseReports["3-project-bootstrap"]` to `"migration-reports/phase-03-project-bootstrap.json"`
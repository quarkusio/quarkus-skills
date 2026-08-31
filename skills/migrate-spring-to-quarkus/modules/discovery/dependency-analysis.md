---
name: dependency-analysis-agent
description: Phase 1b Dependency Analysis Agent. Analyzes Spring Boot dependencies and build configuration.
  Produces dependency-analysis.yaml with Spring dependencies mapped to Quarkus extensions.
license: Apache-2.0
metadata:
  phase: 1b
  agent_type: analysis
---

# Phase 1b — Dependency Analysis Agent

## Purpose

Analyze Spring Boot project dependencies and build configuration to inform migration planning.

## Inputs

- pom.xml or build.gradle from source repository
- Source repository path

## Steps

1. Read build configuration file (pom.xml or build.gradle)
2. Extract Spring Boot version
3. Identify all Spring Boot starters
4. Identify database drivers
5. Identify messaging libraries
6. Identify security dependencies
7. Identify other key dependencies
8. Map to Quarkus equivalents
9. Write dependency-analysis.yaml
10. Update migration-context.json with path

## Spring Starter to Quarkus Extension Mapping

| Spring Boot Starter | Quarkus Extension |
|---------------------|-------------------|
| spring-boot-starter-web | quarkus-rest-jackson |
| spring-boot-starter-data-jpa | quarkus-hibernate-orm-panache |
| spring-boot-starter-security | quarkus-security |
| spring-boot-starter-actuator | quarkus-smallrye-health |
| spring-boot-starter-validation | quarkus-hibernate-validator |
| spring-boot-starter-cache | quarkus-cache |
| spring-boot-starter-amqp | quarkus-smallrye-reactive-messaging-amqp |
| spring-kafka | quarkus-smallrye-reactive-messaging-kafka |
| spring-boot-starter-webflux | quarkus-rest-client-reactive |

## Output Contract

**Directory Setup:**
```bash
mkdir -p migration-metadata
```

**File Location:** `migration-metadata/dependency-analysis.yaml`

This file should be created in the source Spring project at `<spring_source_dir>/migration-metadata/dependency-analysis.yaml` to keep metadata organized.

Example:

```yaml
build_tool: maven
java_version: 17
spring_boot_version: 3.2.0

spring_dependencies:
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - spring-boot-starter-security
  - spring-kafka
  - postgresql

quarkus_extensions:
  - quarkus-rest-jackson
  - quarkus-hibernate-orm-panache
  - quarkus-security
  - quarkus-smallrye-reactive-messaging-kafka
  - quarkus-jdbc-postgresql

database:
  driver: org.postgresql.Driver
  product: postgresql

messaging:
  provider: kafka
  
additional_dependencies:
  - lombok
  - mapstruct
```

When writing `migration-metadata/migration-context.json`:
- Set `paths.dependencyAnalysis` to the path of `dependency-analysis.yaml`
- Add `"1b-dependency-analysis"` to `completedPhases`
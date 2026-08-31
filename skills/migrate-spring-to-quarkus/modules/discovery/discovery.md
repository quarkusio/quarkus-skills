---
name: discovery-agent
description: Phase 1 Discovery Agent. Scans Spring Framework/Spring Boot project to detect technologies, frameworks, and patterns used.
  Produces repo-metadata.json by analyzing source code, configuration files, and dependencies.
license: Apache-2.0
metadata:
  phase: 1
  agent_type: discovery
---

# Phase 1 — Discovery Agent

## Purpose

Produce `repo-metadata.json` by scanning the Spring Framework or Spring Boot source repository.

## Inputs

- Source repository root path (provided by orchestrator)

## Steps

1. Detect project type (Spring Framework standalone or Spring Boot)
2. Scan for Spring Boot application class (@SpringBootApplication) if present
3. Scan for Spring XML configuration files (applicationContext.xml, etc.)
4. Detect Spring/Spring Boot version from pom.xml or build.gradle
5. Scan for Spring annotations:
   - @Service, @Component, @Repository
   - @RestController, @Controller
   - @Configuration, @Bean
   - @Entity, @Table (JPA)
   - @KafkaListener, @RabbitListener, @JmsListener
   - @Async, @Scheduled
   - @Transactional
6. Scan application.properties / application.yml for configuration
7. Identify database configuration
8. Identify messaging configuration
9. Count files by type (controllers, services, repositories, entities)
10. Write repo-metadata.json to `migration-metadata/repo-metadata.json` in the source repository
11. Update `migration-metadata/migration-context.json` with absolute path to repo-metadata.json

## Detection Patterns

### Project Type Detection

**Spring Boot:**
```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**Spring Framework (XML-based):**
```xml
<!-- applicationContext.xml -->
<beans xmlns="http://www.springframework.org/schema/beans">
    <context:component-scan base-package="com.example"/>
</beans>
```

**Spring Framework (Java Config):**
```java
@Configuration
@ComponentScan("com.example")
public class AppConfig {
}
```

### Spring Boot Version
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>
```

### Spring Framework Version
```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>5.3.30</version>
</dependency>
```

### Spring Web
- @RestController
- @Controller
- @RequestMapping, @GetMapping, @PostMapping, etc.

### Spring Data JPA
- @Entity classes
- JpaRepository interfaces
- @Repository annotation

### Spring Kafka
- @KafkaListener
- KafkaTemplate usage

### Spring Security
- SecurityConfig class
- @EnableWebSecurity
- @PreAuthorize, @Secured

### Spring Actuator
- spring-boot-starter-actuator dependency

## Output Contract

**Directory Setup:**
```bash
mkdir -p migration-metadata
```

**File Location:** `migration-metadata/repo-metadata.json`

This file should be created in the source Spring project at `<spring_source_dir>/migration-metadata/repo-metadata.json` to keep metadata organized separately from application code.

Example structure:

```json
{
  "project": {
    "name": "spring-app",
    "type": "Spring Boot",  // or "Spring Framework"
    "spring_version": "5.3.30",  // for Spring Framework
    "spring_boot_version": "3.2.0",  // for Spring Boot
    "java_version": "17",
    "build_tool": "maven",
    "config_type": "annotation"  // or "xml" or "mixed"
  },
  "detected_features": {
    "spring_web": true,
    "spring_data_jpa": true,
    "spring_security": true,
    "spring_kafka": false,
    "spring_rabbitmq": false,
    "spring_actuator": true,
    "spring_cloud": false
  },
  "components": {
    "controllers": [
      {
        "name": "OrderController",
        "path": "src/main/java/com/example/controller/OrderController.java",
        "endpoints": 5
      }
    ],
    "services": [
      {
        "name": "OrderService",
        "path": "src/main/java/com/example/service/OrderService.java"
      }
    ],
    "repositories": [
      {
        "name": "OrderRepository",
        "path": "src/main/java/com/example/repository/OrderRepository.java",
        "type": "JpaRepository"
      }
    ],
    "entities": [
      {
        "name": "Order",
        "path": "src/main/java/com/example/entity/Order.java",
        "table": "orders"
      }
    ]
  },
  "database": {
    "type": "postgresql",
    "driver": "org.postgresql.Driver"
  },
  "messaging": {
    "provider": null
  }
}
```

When writing `migration-metadata/migration-context.json`:
- Set `paths.repoMetadata` to the absolute path of `repo-metadata.json`
- Add `"1-discovery"` to `completedPhases`

## Validation

Before completing, verify:
1. repo-metadata.json exists
2. All detected_features flags are boolean
3. Component counts match actual files
4. `migration-metadata/migration-context.json` updated with path and completedPhases entry
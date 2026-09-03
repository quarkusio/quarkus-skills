# Submodule: Build System (Gradle)

Gradle-specific build migration steps. Called from [build.md](build.md).
Covers both Groovy DSL (`build.gradle`) and Kotlin DSL (`build.gradle.kts`).

Detect which DSL the project uses by the file extension. Use the matching syntax in all examples shown to the user. Do not mix DSLs.

## What to do

- [ ] Replace Spring Boot Gradle plugin with Quarkus Gradle plugin
- [ ] Remove `io.spring.dependency-management` plugin
- [ ] Replace Spring dependency management with Quarkus BOM (`enforcedPlatform`)
- [ ] Configure Java compiler (`-parameters` flag)
- [ ] Configure test task (JBoss LogManager)
- [ ] Replace Spring starters with Quarkus equivalents (use dependency-map.md)
- [ ] Remove unused Spring-only dependencies (`spring-boot-devtools`, etc.)
- [ ] Compile: `./gradlew clean compileJava -x test`

## Plugin Block

**Groovy DSL** (`build.gradle`):

```groovy
// BEFORE: Spring Boot
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.x.x'
    id 'io.spring.dependency-management' version '1.x.x'
}

// AFTER: Quarkus
plugins {
    id 'java'
    id 'io.quarkus' version "${quarkusPlatformVersion}"
}
```

**Kotlin DSL** (`build.gradle.kts`):

```kotlin
// BEFORE: Spring Boot
plugins {
    java
    id("org.springframework.boot") version "3.x.x"
    id("io.spring.dependency-management") version "1.x.x"
}

// AFTER: Quarkus
plugins {
    java
    id("io.quarkus") version(quarkusPlatformVersion)
}
```

## Quarkus BOM

Replace Spring's dependency management with Quarkus BOM using `enforcedPlatform`:

**Groovy DSL**:

```groovy
dependencies {
    implementation enforcedPlatform("io.quarkus.platform:quarkus-bom:${quarkusPlatformVersion}")
    // extension dependencies — no version numbers needed
}
```

**Kotlin DSL**:

```kotlin
dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${quarkusPlatformVersion}"))
    // extension dependencies — no version numbers needed
}
```

Define the version in `gradle.properties`:

```properties
quarkusPlatformVersion=3.x.x
```

Do NOT hardcode the version in the build file — use the latest Quarkus release.

## Java Compiler Configuration

**Groovy DSL**:

```groovy
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

compileJava {
    options.encoding = 'UTF-8'
    options.compilerArgs.add('-parameters')
}
```

**Kotlin DSL**:

```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
```

## Test Configuration

**Groovy DSL**:

```groovy
test {
    systemProperty 'java.util.logging.manager', 'org.jboss.logmanager.LogManager'
}
```

**Kotlin DSL**:

```kotlin
tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
```

## Native Build Support

Unlike Maven, Gradle does not need a separate profile. The Quarkus Gradle plugin registers native tasks automatically:

```bash
# Build a native image
./gradlew build -Dquarkus.native.enabled=true
```

## Complete Before/After Example

**Groovy DSL** (`build.gradle`):

```groovy
// BEFORE: Full Spring Boot build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = '21'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.mysql:mysql-connector-j'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

test {
    useJUnitPlatform()
}

// AFTER: Quarkus build.gradle
plugins {
    id 'java'
    id 'io.quarkus' version "${quarkusPlatformVersion}"
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = '21'
}

compileJava {
    options.encoding = 'UTF-8'
    options.compilerArgs.add('-parameters')
}

dependencies {
    implementation enforcedPlatform("io.quarkus.platform:quarkus-bom:${quarkusPlatformVersion}")
    implementation 'io.quarkus:quarkus-rest'
    implementation 'io.quarkus:quarkus-hibernate-orm-panache'
    implementation 'io.quarkus:quarkus-jdbc-mysql'
    testImplementation 'io.quarkus:quarkus-junit5'
    testImplementation 'io.rest-assured:rest-assured'
}

test {
    systemProperty 'java.util.logging.manager', 'org.jboss.logmanager.LogManager'
}
```

## Gradle-specific watch out

- **`io.spring.dependency-management` plugin**: Must be removed entirely. Quarkus uses `enforcedPlatform` instead. Leaving both causes version conflicts.
- **`bootJar` / `bootRun` tasks**: These are Spring Boot plugin tasks. After removing the Spring Boot plugin, they no longer exist. The Quarkus plugin provides `quarkusBuild` and `quarkusDev` instead.
- **Gradle wrapper**: If the project has `gradlew`/`gradlew.bat`, always use `./gradlew` instead of a system-installed `gradle` command.
- **Multi-project builds**: If the Spring Boot app is a subproject in a multi-project Gradle build, apply the Quarkus plugin only to the subproject, not the root. The BOM should also be scoped to that subproject's dependencies.
- **Groovy vs Kotlin DSL**: Detect which DSL the project uses by the file extension (`.gradle` vs `.gradle.kts`). Use the matching syntax. Do not mix DSLs.
- **`settings.gradle(.kts)`**: Some Spring Boot projects configure plugin management in the settings file. After migration, the Quarkus plugin resolves from the Gradle Plugin Portal by default — no special plugin management is needed.
- **Run Gradle on a JDK ≥ the project's target Java version**: `quarkusBuild` runs Quarkus augmentation inside the Gradle JVM and loads the project's compiled classes. If Gradle itself runs on an older JDK than the code targets, `compileJava` can still pass (via toolchains / `--release`) while `quarkusBuild` fails with `UnsupportedClassVersionError`. Point `JAVA_HOME` (or a Gradle toolchain) at a JDK matching the target version, and use the same JDK in CI and the Docker build stage.
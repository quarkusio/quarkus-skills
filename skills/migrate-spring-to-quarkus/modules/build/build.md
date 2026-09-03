# Module: Build System

Migrate the build descriptor and configuration files from Spring Boot to Quarkus.

## Instructions

- Detect the build tool by checking which files exist at the project root:

| File | Build tool | Sub-module |
|---|---|---|
| `pom.xml` | Maven | [maven.md](maven.md) |
| `build.gradle` or `build.gradle.kts` | Gradle | [gradle.md](gradle.md) |

- Load [references/dependency-map.md](../../references/dependency-map.md) and [references/config-map.md](../../references/config-map.md) before starting.
- Then load and execute the matching submodule above.
- After the submodule completes, return here and continue with the Configuration Migration and Watch Out sections below.

## Configuration Migration

Rename Spring properties to Quarkus equivalents using config-map.md. Key mappings:

- `spring.datasource.*` → `%prod.quarkus.datasource.*` (see below)
- `spring.jpa.*` → `quarkus.hibernate-orm.*`
- `server.port` → `quarkus.http.port`
- `logging.level.*` → `quarkus.log.category."*".level`

### Datasource properties and Dev Services

When the project has no `application-{profile}.properties` files, prefix datasource connection properties with `%prod.` so they only apply in production. This lets Quarkus Dev Services automatically start a containerized database in dev and test modes — no local database setup needed.

```properties
# connection details only for prod
%prod.quarkus.datasource.jdbc.url=jdbc:mysql://127.0.0.1:3306/todo
%prod.quarkus.datasource.username=root
%prod.quarkus.datasource.password=root
```

If `application-{profile}.properties` files exist, place production datasource config in `application-prod.properties` instead (no `%prod.` prefix needed there).

## Watch out

- **Profile handling**: Spring's `application-{profile}.properties` → Quarkus `%profile.` prefix in a single `application.properties`
- **Naming strategy mismatch**: Spring Boot defaults to snake_case (`firstName` → `first_name`). Quarkus/Hibernate 6 preserves camelCase. Set `quarkus.hibernate-orm.physical-naming-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy`. **Also update `import.sql`/`data.sql` column names**.
- **`quarkus-spring-boot-properties`** (Spring compat only): `@ConstructorBinding` NOT supported (needs no-arg constructor + setters). `Map<K,V>` types NOT supported.
- **Build tool wrapper**: If the project has `mvnw`/`gradlew`, always use `./mvnw` or `./gradlew` instead of the system-installed `mvn` or `gradle` command. This ensures reproducible builds with the exact tool version the project expects.
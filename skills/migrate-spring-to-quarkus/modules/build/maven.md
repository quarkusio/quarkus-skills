# Submodule: Build System (Maven)

Maven-specific build migration steps. Called from [build.md](build.md).

## What to do

- [ ] Replace Spring Boot parent with Quarkus BOM
- [ ] Replace `spring-boot-maven-plugin` with `quarkus-maven-plugin`
- [ ] Update `maven-compiler-plugin` and `maven-surefire-plugin`
- [ ] Add `native` profile
- [ ] Replace Spring starters with Quarkus equivalents (use dependency-map.md)
- [ ] Remove unused Spring-only dependencies (`spring-boot-devtools`, etc.)
- [ ] Compile: `./mvnw clean compile -DskipTests`

## pom.xml Reference Snippets

**Remove** the Spring Boot parent:
```xml
<!-- DELETE this -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>...</version>
</parent>
```

**Add** Quarkus BOM in `<dependencyManagement>`:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Add** Quarkus plugin and update compiler/surefire:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-maven-plugin</artifactId>
            <version>${quarkus.platform.version}</version>
            <extensions>true</extensions>
            <executions>
                <execution>
                    <goals>
                        <goal>build</goal>
                        <goal>generate-code</goal>
                        <goal>generate-code-tests</goal>
                        <goal>native-image-agent</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>${compiler-plugin.version}</version>
            <configuration>
                <parameters>true</parameters>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>${surefire-plugin.version}</version>
            <configuration>
                <systemPropertyVariables>
                    <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                </systemPropertyVariables>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Define `quarkus.platform.version` as a Maven property. Do NOT hardcode the version — use the latest Quarkus release.

**Add** Quarkus native profile:

```xml
<profiles>
    <profile>
        <id>native</id>
        <activation>
            <property>
                <name>native</name>
            </property>
        </activation>
        <properties>
            <quarkus.package.jar.enabled>false</quarkus.package.jar.enabled>
            <skipITs>false</skipITs>
            <quarkus.native.enabled>true</quarkus.native.enabled>
        </properties>
    </profile>
</profiles>
```
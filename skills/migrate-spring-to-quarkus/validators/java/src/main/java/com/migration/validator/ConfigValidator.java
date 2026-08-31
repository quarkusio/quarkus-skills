package com.migration.validator;

import com.migration.validator.core.ValidationReport;
import com.migration.validator.core.YamlUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ConfigValidator - Validates configuration migration from Spring Boot to
 * Quarkus (Phase 9)
 * Instance-based validator with constructor injection for better testability
 * and design.
 *
 * Full migration mode validates:
 * 1. Spring properties migrated to Quarkus equivalents
 * 2. Property values are compatible
 * 3. No Spring-specific properties remain
 * 4. Maven compile succeeds
 *
 * Compat mode checks (--compat-mode):
 * C1. quarkus-spring-di present in pom.xml (when service_layer =
 * spring-di-compat)
 * C2. No RestTemplate beans remain (not bridged by quarkus-spring-di)
 * C3. No SpEL @Value("#{…}") remains — property placeholder @Value("${…}") IS
 * bridged
 * C4. quarkus-spring-boot-properties present (when config_properties =
 * spring-boot-properties-compat)
 * C5. No Map<K,V> fields in @ConfigurationProperties classes (throws
 * DeploymentException)
 * C6. No @ConstructorBinding remains (compat requires no-arg constructor +
 * setters)
 * C7. Property migration and no Spring properties checks (always apply)
 * C8. Maven compile succeeds
 */
public class ConfigValidator {

    private static final String PHASE = "phase_9_configuration";

    private final Path springProject;
    private final Path quarkusProject;
    private final Path specPath;
    private final boolean compatMode;

    // Property mappings from Spring to Quarkus
    private static final Map<String, String> PROPERTY_MAPPINGS = new HashMap<>();
    static {
        // Server properties
        PROPERTY_MAPPINGS.put("server.port", "quarkus.http.port");
        PROPERTY_MAPPINGS.put("server.address", "quarkus.http.host");
        PROPERTY_MAPPINGS.put("server.servlet.context-path", "quarkus.http.root-path");
        PROPERTY_MAPPINGS.put("server.servlet.contextPath", "quarkus.http.root-path");
        PROPERTY_MAPPINGS.put("server.compression.enabled", "quarkus.http.enable-compression");

        // Datasource properties
        PROPERTY_MAPPINGS.put("spring.datasource.url", "quarkus.datasource.jdbc.url");
        PROPERTY_MAPPINGS.put("spring.datasource.username", "quarkus.datasource.username");
        PROPERTY_MAPPINGS.put("spring.datasource.password", "quarkus.datasource.password");
        PROPERTY_MAPPINGS.put("spring.datasource.driver-class-name", "quarkus.datasource.jdbc.driver");

        // Connection pool properties
        PROPERTY_MAPPINGS.put("spring.datasource.hikari.maximumPoolSize", "quarkus.datasource.jdbc.max-size");
        PROPERTY_MAPPINGS.put("spring.datasource.hikari.minimumIdle", "quarkus.datasource.jdbc.min-size");
        PROPERTY_MAPPINGS.put("spring.datasource.hikari.connectionTimeout",
                "quarkus.datasource.jdbc.acquisition-timeout");
        PROPERTY_MAPPINGS.put("spring.datasource.hikari.leak-detection-threshold",
                "quarkus.datasource.jdbc.leak-detection-interval");

        // JPA/Hibernate properties
        PROPERTY_MAPPINGS.put("spring.jpa.hibernate.ddl-auto", "quarkus.hibernate-orm.database.generation");
        PROPERTY_MAPPINGS.put("spring.jpa.show-sql", "quarkus.hibernate-orm.log.sql");
        PROPERTY_MAPPINGS.put("spring.jpa.properties.hibernate.format_sql", "quarkus.hibernate-orm.log.format-sql");
        PROPERTY_MAPPINGS.put("spring.jpa.properties.hibernate.dialect", "quarkus.hibernate-orm.dialect");
        PROPERTY_MAPPINGS.put("spring.jpa.database-platform", "quarkus.hibernate-orm.dialect");

        // Kafka properties
        PROPERTY_MAPPINGS.put("spring.kafka.bootstrap-servers", "kafka.bootstrap.servers");

        // Logging properties
        PROPERTY_MAPPINGS.put("logging.level.root", "quarkus.log.level");
        PROPERTY_MAPPINGS.put("logging.file.name", "quarkus.log.file.path");

        // Actuator properties
        PROPERTY_MAPPINGS.put("management.endpoints.web.base-path", "quarkus.smallrye-health.root-path");
    }

    // Value transformations
    private static final Map<String, Map<String, String>> VALUE_TRANSFORMATIONS = new HashMap<>();
    static {
        Map<String, String> ddlAuto = new HashMap<>();
        ddlAuto.put("create", "drop-and-create");
        ddlAuto.put("create-drop", "drop-and-create");
        ddlAuto.put("update", "update");
        ddlAuto.put("validate", "validate");
        ddlAuto.put("none", "none");
        VALUE_TRANSFORMATIONS.put("spring.jpa.hibernate.ddl-auto", ddlAuto);
    }

    // Properties that don't need migration (handled automatically by Quarkus or not
    // applicable)
    private static final Set<String> OPTIONAL_PROPERTIES = new HashSet<>();
    static {
        OPTIONAL_PROPERTIES.add("spring.jpa.open-in-view");
        OPTIONAL_PROPERTIES.add("spring.jpa.properties.hibernate.current_session_context_class");
        OPTIONAL_PROPERTIES.add("spring.application.name");
        OPTIONAL_PROPERTIES.add("spring.jpa.properties.hibernate.enable_lazy_load_no_trans");
        OPTIONAL_PROPERTIES.add("spring.jpa.properties.jakarta.persistence.sharedCache.mode");
        OPTIONAL_PROPERTIES.add("spring.datasource.initialize");
        OPTIONAL_PROPERTIES.add("spring.jpa.defer-datasource-initialization");
        OPTIONAL_PROPERTIES.add("spring.sql.init.mode");

        // ActiveMQ properties - replaced by Quarkus messaging
        OPTIONAL_PROPERTIES.add("spring.activemq.broker-url");
        OPTIONAL_PROPERTIES.add("spring.activemq.in-memory");
        OPTIONAL_PROPERTIES.add("spring.activemq.packages.trust-all");

        // Spring Batch properties - if batch is not migrated
        OPTIONAL_PROPERTIES.add("spring.batch.jdbc.initialize-schema");

        // MIME mappings - handled by Quarkus automatically
        OPTIONAL_PROPERTIES.add("server.mime-mappings.ttf");
        OPTIONAL_PROPERTIES.add("server.mime-mappings.woff");
        OPTIONAL_PROPERTIES.add("server.mime-mappings.woff2");
        OPTIONAL_PROPERTIES.add("server.mime-mappings.eot");

        // JSF/JoinFaces properties - framework-specific
        OPTIONAL_PROPERTIES.add("joinfaces.faces.project-stage");
        OPTIONAL_PROPERTIES.add("joinfaces.faces.client-window-mode");
        OPTIONAL_PROPERTIES.add("joinfaces.primefaces.theme");
        OPTIONAL_PROPERTIES.add("joinfaces.primefaces.font-awesome");
        OPTIONAL_PROPERTIES.add("joinfaces.primefaces.move-scripts-to-bottom");
    }

    /**
     * Constructor for full migration mode.
     *
     * @param springProject  Absolute path to the Spring project root
     * @param quarkusProject Absolute path to the Quarkus project root
     * @param specPath       Absolute path to migration-spec.yaml
     */
    public ConfigValidator(Path springProject, Path quarkusProject, Path specPath) {
        this(springProject, quarkusProject, specPath, false);
    }

    /**
     * Constructor with compat mode flag.
     *
     * @param springProject  Absolute path to the Spring project root
     * @param quarkusProject Absolute path to the Quarkus project root
     * @param specPath       Absolute path to migration-spec.yaml
     * @param compatMode     When true, runs compat-mode checks instead of full
     *                       migration checks
     */
    public ConfigValidator(Path springProject, Path quarkusProject, Path specPath, boolean compatMode) {
        this.springProject = springProject.toAbsolutePath();
        this.quarkusProject = quarkusProject.toAbsolutePath();
        this.specPath = specPath.toAbsolutePath();
        this.compatMode = compatMode;
    }

    /**
     * Run validation and return exit code.
     *
     * @param verbose Enable verbose output with detailed logging
     * @return 0 for success, 1 for failure
     */
    public int validate(boolean verbose) {
        try {
            ValidationReport report = runValidation();

            // Load existing spec
            Map<String, Object> spec = YamlUtils.loadYaml(specPath);

            // Save report to spec
            saveToSpec(spec, report);

            // Print summary
            report.printSummary(PHASE);

            // Return exit code
            return report.hasFailures() ? 1 : 0;

        } catch (Exception e) {
            System.err.println("Validation failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * Internal validation method — dispatches to compat or full path.
     */
    private ValidationReport runValidation() throws IOException {
        ValidationReport report = new ValidationReport();

        // Load Spring and Quarkus properties (needed in both modes)
        Map<String, String> springProps = loadSpringProperties(this.springProject);
        Map<String, String> quarkusProps = loadQuarkusProperties(this.quarkusProject);

        if (compatMode) {
            System.out.println("[INFO] Running compat-mode validation checks\n");

            // Load spec to inspect migration_strategy flags
            Map<String, Object> spec = loadSpec();

            String serviceLayer = stringValue(YamlUtils.getValue(spec, "migration_strategy.service_layer"));
            String configProperties = stringValue(YamlUtils.getValue(spec, "migration_strategy.config_properties"));

            boolean springDiCompat = "spring-di-compat".equals(serviceLayer);
            boolean springBootPropertiesCompat = "spring-boot-properties-compat".equals(configProperties);

            // C1 — quarkus-spring-di must be present when service_layer = spring-di-compat
            if (springDiCompat) {
                checkExtensionPresent("quarkus-spring-di",
                        "service_layer = spring-di-compat", report);
            }

            // C2 — RestTemplate is a Spring Web concern; no compat bridge exists
            checkNoRestTemplateBeans(report);

            // C3 — SpEL @Value("#{…}") is NOT bridged; property placeholder @Value("${…}")
            // IS bridged
            checkNoSpelValueAnnotations(report);

            // C4 — quarkus-spring-boot-properties must be present when config_properties =
            // spring-boot-properties-compat
            if (springBootPropertiesCompat) {
                checkExtensionPresent("quarkus-spring-boot-properties",
                        "config_properties = spring-boot-properties-compat", report);

                // C5 — Map<K,V> fields in @ConfigurationProperties throw DeploymentException
                checkNoMapFieldsInConfigurationProperties(report);

                // C6 — @ConstructorBinding requires no-arg constructor + setters with compat
                // extension
                checkNoConstructorBinding(report);
            }

            // Property migration and Spring properties checks still apply in compat mode
            validatePropertyMigration(springProps, quarkusProps, report);
            validateNoSpringProperties(quarkusProps, report);

        } else {
            System.out.println("[INFO] Running full migration validation checks\n");

            validatePropertyMigration(springProps, quarkusProps, report);
            validateNoSpringProperties(quarkusProps, report);
        }

        // Maven compile always checked last
        validateMavenCompile(report);

        return report;
    }

    // -----------------------------------------------------------------------
    // Compat-mode checks
    // -----------------------------------------------------------------------

    /**
     * Verify that a required compat extension is present in pom.xml.
     */
    private void checkExtensionPresent(String artifactId, String reason, ValidationReport report) {
        String rule = artifactId + " present in pom.xml (" + reason + ")";
        System.out.println("[CHECK] " + rule);

        Path pomPath = quarkusProject.resolve("pom.xml");
        if (!pomPath.toFile().exists()) {
            report.fail(rule, "pom.xml not found in project root");
            return;
        }
        try {
            String pomContent = Files.readString(pomPath);
            if (pomContent.contains(artifactId)) {
                report.pass(rule, artifactId + " found in pom.xml");
            } else {
                report.fail(rule, artifactId + " not found in pom.xml — add the extension for compat mode");
            }
        } catch (IOException e) {
            report.fail(rule, "Error reading pom.xml: " + e.getMessage());
        }
    }

    /**
     * C2 — RestTemplate beans must be replaced with JAX-RS Client or MicroProfile
     * REST Client.
     * quarkus-spring-di does NOT bridge RestTemplate (it is a Spring Web concern,
     * not DI).
     */
    private void checkNoRestTemplateBeans(ValidationReport report) {
        String rule = "No RestTemplate @Bean methods remain (not bridged by quarkus-spring-di)";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(quarkusProject);
            // Match @Bean methods that return RestTemplate
            Pattern restTemplateBeanPattern = Pattern.compile(
                    "@Bean[^;{]*\\n[^;{]*RestTemplate\\s+\\w+\\s*\\(");
            // Also catch inline: @Bean public RestTemplate ...
            Pattern inlinePattern = Pattern.compile(
                    "@Bean\\b.*\\bRestTemplate\\b");

            List<String> violations = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);
                if (restTemplateBeanPattern.matcher(activeContent).find()
                        || inlinePattern.matcher(activeContent).find()) {
                    violations.add(file.getFileName().toString());
                }
            }

            if (violations.isEmpty()) {
                report.pass(rule, "No RestTemplate @Bean methods found");
            } else {
                report.fail(rule, String.format(
                        "RestTemplate @Bean in %d file(s): %s — replace with jakarta.ws.rs.client.Client or a @RegisterRestClient interface.",
                        violations.size(),
                        String.join(", ", violations.subList(0, Math.min(3, violations.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    /**
     * C3 — SpEL @Value("#{…}") is not bridged; property placeholder @Value("${…}")
     * IS bridged.
     */
    private void checkNoSpelValueAnnotations(ValidationReport report) {
        String rule = "No SpEL @Value(\"#{…}\") expressions remain (property placeholder @Value(\"${…}\") is bridged)";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(quarkusProject);
            Pattern spelPattern = Pattern.compile("@Value\\s*\\(\\s*\"#\\{");
            List<String> violations = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                if (spelPattern.matcher(removeComments(content)).find()) {
                    violations.add(file.getFileName().toString());
                }
            }

            if (violations.isEmpty()) {
                report.pass(rule,
                        "No SpEL @Value expressions found — property placeholder @Value is bridged by quarkus-spring-di");
            } else {
                report.fail(rule, String.format(
                        "SpEL @Value(\"#{…}\") in %d file(s): %s — SpEL is not supported; rewrite to a CDI equivalent.",
                        violations.size(),
                        String.join(", ", violations.subList(0, Math.min(3, violations.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    /**
     * C5 — Map&lt;K,V&gt; fields in @ConfigurationProperties classes cause a
     * DeploymentException
     * at startup when quarkus-spring-boot-properties is used; they must be replaced
     * with a
     * 
     * @ConfigMapping interface that exposes Map&lt;String, String&gt;.
     */
    private void checkNoMapFieldsInConfigurationProperties(ValidationReport report) {
        String rule = "No Map<K,V> fields in @ConfigurationProperties classes (causes DeploymentException with compat extension)";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(quarkusProject);
            Pattern configPropsPattern = Pattern.compile("@ConfigurationProperties\\b");
            // Match Map<…> as a field declaration: access modifier (or @…\n) followed by
            // Map<
            // e.g. "private Map<String, String> labels" or " Map<String, String> labels"
            // Does NOT match method return types (those are preceded by a modifier on the
            // same line)
            // Uses DOTALL so \s covers newlines.
            Pattern mapFieldPattern = Pattern.compile(
                    "(?:private|protected|public|@\\w+[^;{]*\\n)\\s+(?:final\\s+)?Map\\s*<",
                    Pattern.DOTALL);
            List<String> violations = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);
                if (configPropsPattern.matcher(activeContent).find()
                        && mapFieldPattern.matcher(activeContent).find()) {
                    violations.add(file.getFileName().toString());
                }
            }

            if (violations.isEmpty()) {
                report.pass(rule, "No Map fields found in @ConfigurationProperties classes");
            } else {
                report.fail(rule, String.format(
                        "Map<K,V> field(s) in @ConfigurationProperties class in %d file(s): %s — replace with a @ConfigMapping interface exposing Map<String, String>.",
                        violations.size(),
                        String.join(", ", violations.subList(0, Math.min(3, violations.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    /**
     * C6 — @ConstructorBinding is not supported by quarkus-spring-boot-properties
     * compat.
     * The extension requires a no-arg constructor + setter methods.
     */
    private void checkNoConstructorBinding(ValidationReport report) {
        String rule = "No @ConstructorBinding remains (quarkus-spring-boot-properties compat requires no-arg constructor + setters)";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(quarkusProject);
            Pattern constructorBindingPattern = Pattern.compile("@ConstructorBinding\\b");
            List<String> violations = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                if (constructorBindingPattern.matcher(removeComments(content)).find()) {
                    violations.add(file.getFileName().toString());
                }
            }

            if (violations.isEmpty()) {
                report.pass(rule, "No @ConstructorBinding found");
            } else {
                report.fail(rule, String.format(
                        "@ConstructorBinding in %d file(s): %s — remove @ConstructorBinding and add a no-arg constructor with setter methods.",
                        violations.size(),
                        String.join(", ", violations.subList(0, Math.min(3, violations.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Shared checks (both modes)
    // -----------------------------------------------------------------------

    /**
     * Load Spring Boot properties from application.properties
     */
    private Map<String, String> loadSpringProperties(Path projectDir) throws IOException {
        Map<String, String> properties = new HashMap<>();

        Path propsFile = projectDir.resolve("src/main/resources/application.properties");
        if (Files.exists(propsFile)) {
            String content = Files.readString(propsFile);
            properties.putAll(parsePropertiesFile(content));
        }

        return properties;
    }

    /**
     * Load Quarkus properties from application.properties
     */
    private Map<String, String> loadQuarkusProperties(Path projectDir) throws IOException {
        Map<String, String> properties = new HashMap<>();

        Path propsFile = projectDir.resolve("src/main/resources/application.properties");
        if (Files.exists(propsFile)) {
            String content = Files.readString(propsFile);
            properties.putAll(parsePropertiesFile(content));
        }

        return properties;
    }

    /**
     * Parse properties file content
     */
    private Map<String, String> parsePropertiesFile(String content) {
        Map<String, String> properties = new HashMap<>();

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Parse key=value
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                properties.put(key, value);
            }
        }

        return properties;
    }

    /**
     * Validate that Spring properties are migrated to Quarkus
     */
    private void validatePropertyMigration(Map<String, String> springProps,
            Map<String, String> quarkusProps,
            ValidationReport report) {
        int totalSpringProps = 0;
        int migratedProps = 0;
        int optionalProps = 0;
        List<String> missingProps = new ArrayList<>();
        List<String> valueMismatches = new ArrayList<>();

        for (Map.Entry<String, String> entry : springProps.entrySet()) {
            String springKey = entry.getKey();
            String springValue = entry.getValue();

            // Skip non-Spring properties
            if (!springKey.startsWith("spring.") &&
                    !springKey.startsWith("server.") &&
                    !springKey.startsWith("logging.") &&
                    !springKey.startsWith("management.")) {
                continue;
            }

            totalSpringProps++;

            // Check if property has a Quarkus equivalent
            String quarkusKey = PROPERTY_MAPPINGS.get(springKey);

            if (quarkusKey == null) {
                // Check if it's an optional property
                if (OPTIONAL_PROPERTIES.contains(springKey)) {
                    optionalProps++;
                    continue;
                }

                // Unmapped property
                missingProps.add(springKey);
                continue;
            }

            // Find Quarkus property (check with and without profile prefix)
            String quarkusValue = findQuarkusProperty(quarkusKey, quarkusProps);

            if (quarkusValue != null) {
                migratedProps++;

                // Check if values are compatible
                String expectedValue = transformValue(springKey, springValue);
                if (!valuesCompatible(expectedValue, quarkusValue)) {
                    valueMismatches.add(String.format("%s: %s → %s (expected: %s)",
                            springKey, springValue, quarkusValue, expectedValue));
                }
            } else {
                missingProps.add(springKey + " → " + quarkusKey);
            }
        }

        // Report results
        if (totalSpringProps == 0) {
            report.pass("property_migration",
                    "No Spring properties found to migrate");
        } else {
            int expectedMigrated = totalSpringProps - optionalProps;
            if (migratedProps == expectedMigrated) {
                report.pass("property_migration",
                        String.format("All %d required properties migrated (%d optional skipped)",
                                migratedProps, optionalProps));
            } else {
                report.fail("property_migration",
                        String.format("%d/%d properties migrated. Missing:\n  - %s",
                                migratedProps, expectedMigrated, String.join("\n  - ", missingProps)));
            }
        }

        // Report value mismatches
        if (!valueMismatches.isEmpty()) {
            report.fail("property_values",
                    String.format("Property value mismatches:\n  - %s",
                            String.join("\n  - ", valueMismatches)));
        } else if (migratedProps > 0) {
            report.pass("property_values",
                    "All migrated property values are compatible");
        }
    }

    /**
     * Find Quarkus property, checking with and without profile prefixes
     */
    private String findQuarkusProperty(String key, Map<String, String> quarkusProps) {
        // Try exact match first
        if (quarkusProps.containsKey(key)) {
            return quarkusProps.get(key);
        }

        // Try with profile prefixes
        String[] profiles = { "%dev.", "%test.", "%prod." };
        for (String profile : profiles) {
            String profileKey = profile + key;
            if (quarkusProps.containsKey(profileKey)) {
                return quarkusProps.get(profileKey);
            }
        }

        return null;
    }

    /**
     * Transform Spring property value to Quarkus equivalent
     */
    private String transformValue(String key, String value) {
        Map<String, String> transformations = VALUE_TRANSFORMATIONS.get(key);
        if (transformations != null && transformations.containsKey(value)) {
            return transformations.get(value);
        }
        return value;
    }

    /**
     * Check if two property values are compatible
     */
    private boolean valuesCompatible(String value1, String value2) {
        if (value1 == null && value2 == null)
            return true;
        if (value1 == null || value2 == null)
            return false;

        // Normalize values
        value1 = value1.trim().toLowerCase();
        value2 = value2.trim().toLowerCase();

        // Exact match
        if (value1.equals(value2))
            return true;

        // Boolean equivalence
        if ((value1.equals("true") || value1.equals("false")) &&
                (value2.equals("true") || value2.equals("false"))) {
            return value1.equals(value2);
        }

        // Duration equivalence (milliseconds to duration format)
        if (isDuration(value2)) {
            try {
                long millis1 = Long.parseLong(value1);
                long millis2 = parseDuration(value2);
                return millis1 == millis2;
            } catch (NumberFormatException e) {
                // Not a duration, continue
            }
        }

        // Numeric equivalence
        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return Math.abs(num1 - num2) < 0.0001;
        } catch (NumberFormatException e) {
            // Not numeric, continue
        }

        // Path equivalence (handle different separators)
        if (value1.contains("/") || value1.contains("\\") ||
                value2.contains("/") || value2.contains("\\")) {
            String path1 = value1.replace("\\", "/");
            String path2 = value2.replace("\\", "/");
            return path1.equals(path2);
        }

        return false;
    }

    /**
     * Check if a value is in duration format (e.g., 5S, 10M, 2H)
     */
    private boolean isDuration(String value) {
        if (value == null || value.isEmpty())
            return false;
        value = value.toLowerCase();
        return value.endsWith("ms") || value.endsWith("s") ||
                value.endsWith("m") || value.endsWith("h") ||
                value.endsWith("d");
    }

    /**
     * Parse duration string to milliseconds.
     * Supports: ms (milliseconds), s (seconds), m (minutes), h (hours), d (days)
     */
    private long parseDuration(String duration) {
        duration = duration.toLowerCase().trim();

        if (duration.endsWith("ms")) {
            return Long.parseLong(duration.substring(0, duration.length() - 2));
        } else if (duration.endsWith("s")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 1000;
        } else if (duration.endsWith("m")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60 * 1000;
        } else if (duration.endsWith("h")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60 * 60 * 1000;
        } else if (duration.endsWith("d")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 24 * 60 * 60 * 1000;
        }

        throw new NumberFormatException("Invalid duration format: " + duration);
    }

    /**
     * Validate that no Spring-specific properties remain in Quarkus config
     */
    private void validateNoSpringProperties(Map<String, String> quarkusProps, ValidationReport report) {
        List<String> springProps = new ArrayList<>();

        for (String key : quarkusProps.keySet()) {
            // Remove profile prefix if present
            String cleanKey = key;
            if (key.startsWith("%")) {
                int dotIndex = key.indexOf('.');
                if (dotIndex > 0) {
                    cleanKey = key.substring(dotIndex + 1);
                }
            }

            // Check if it's a Spring property
            if (cleanKey.startsWith("spring.")) {
                springProps.add(key);
            }
        }

        if (springProps.isEmpty()) {
            report.pass("no_spring_properties",
                    "No Spring properties found in Quarkus configuration");
        } else {
            report.fail("no_spring_properties",
                    String.format("Spring properties still present:\n  - %s\nRemove Spring-specific properties.",
                            String.join("\n  - ", springProps)));
        }
    }

    /**
     * Validate Maven compile succeeds
     */
    private void validateMavenCompile(ValidationReport report) {
        String rule = "Maven compile succeeds";
        System.out.println("[CHECK] " + rule);

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "compile", "-q");
            pb.directory(this.quarkusProject.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                report.fail(rule, "Maven compile timed out after 300 seconds");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                report.pass(rule, "Maven compile successful");
            } else {
                String errorSnippet = output.toString().lines()
                        .filter(l -> l.contains("ERROR"))
                        .limit(3)
                        .collect(Collectors.joining("\n  "));
                report.fail(rule, "Maven compile failed:\n  " + errorSnippet);
            }

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Cannot run program \"mvn\"")) {
                report.fail(rule, "Maven (mvn) not found in PATH");
            } else {
                report.fail(rule, "Error running Maven: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        } catch (InterruptedException e) {
            report.fail(rule, "Maven process interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> loadSpec() {
        try {
            return YamlUtils.loadYaml(specPath);
        } catch (IOException e) {
            System.err.println("[WARNING] Could not load spec for compat-mode flags: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private List<Path> findJavaFiles(Path baseDir) throws IOException {
        try (Stream<Path> paths = Files.walk(baseDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private String removeComments(String content) {
        // Remove single-line comments
        content = content.replaceAll("//[^\n]*", "");
        // Remove multi-line comments
        content = content.replaceAll("/\\*.*?\\*/", "");
        return content;
    }

    @SuppressWarnings("unchecked")
    private void saveToSpec(Map<String, Object> spec, ValidationReport report) {
        try {
            // Ensure intermediate.history exists
            Map<String, Object> intermediate = (Map<String, Object>) spec.computeIfAbsent("intermediate",
                    k -> new HashMap<>());
            List<Map<String, Object>> history = (List<Map<String, Object>>) intermediate.computeIfAbsent("history",
                    k -> new ArrayList<>());

            // Add this validation run to history
            history.add(report.toMap(PHASE));

            // Save updated spec
            YamlUtils.saveYaml(this.specPath, spec);

        } catch (IOException e) {
            System.err.println("[WARNING] Could not save to spec: " + e.getMessage());
        }
    }
}

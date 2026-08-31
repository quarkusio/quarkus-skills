package com.migration.validator;

import com.migration.validator.core.ValidationReport;
import com.migration.validator.core.YamlUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ServiceValidator - Phase 6 validator for Spring to Quarkus migration.
 * Instance-based validator with constructor injection for better testability
 * and design.
 *
 * Validates Spring service layer migration to Quarkus CDI beans.
 *
 * Checks 18 validation rules (full migration):
 * 1. No Spring stereotype annotations
 * (@Service, @Component, @Repository, @Autowired)
 * 2. No Spring imports
 * 3. CDI scope annotations present (@ApplicationScoped, etc.)
 * 4. @Inject usage (not @Autowired)
 * 5. @ConfigProperty migration (from @Value)
 * 6. @Transactional migration (Spring → Jakarta)
 * 7. @Async migration
 * 8. @Scheduled migration
 * 9. No ApplicationContext usage
 * 10. Lifecycle hooks use jakarta.annotation
 * 11. No Spring @Configuration classes
 * 12. @Bean migrated to @Produces
 * 13. ApplicationEventPublisher migrated to CDI events
 * 14. No Spring Boot annotations
 * 15. No Spring context utilities
 * 16. @JmsListener migrated to @Incoming
 * 17. Spring scope annotations migrated to CDI
 * 18. Maven compile succeeds
 *
 * Compat mode checks (--compat-mode):
 * C1. quarkus-spring-di extension present
 * C2. No SpEL @Value("#{…}") remains — property placeholder @Value("${…}") is
 * bridged and allowed
 * C3. Spring @Transactional import replaced OR quarkus-spring-tx extension
 * present (when compat_mode.spring_tx=true)
 * C4. No unsupported compat annotations (@Primary, @Lazy, @Profile)
 * C5. Spring @Scheduled import replaced OR quarkus-spring-scheduled extension
 * present (when compat_mode.spring_scheduled=true)
 * C6. Maven compile succeeds
 */
public class ServiceValidator {

    private final Path targetDir;
    private final Path specPath;
    private final boolean compatMode;
    private final ValidationReport report;
    private Map<String, Object> spec;

    /**
     * Constructor with dependency injection (full migration mode).
     *
     * @param targetDir Absolute path to the target Quarkus project root
     * @param specPath  Absolute path to migration-spec.yaml
     */
    public ServiceValidator(Path targetDir, Path specPath) {
        this(targetDir, specPath, false);
    }

    /**
     * Constructor with dependency injection.
     *
     * @param targetDir  Absolute path to the target Quarkus project root
     * @param specPath   Absolute path to migration-spec.yaml
     * @param compatMode When true, allows Spring DI annotations and checks for
     *                   quarkus-spring-di extension
     */
    public ServiceValidator(Path targetDir, Path specPath, boolean compatMode) {
        this.targetDir = targetDir.toAbsolutePath();
        this.specPath = specPath.toAbsolutePath();
        this.compatMode = compatMode;
        this.report = new ValidationReport();
    }

    /**
     * Run validation and return exit code.
     *
     * @param verbose Enable verbose output with detailed logging
     * @return 0 for success, 1 for failure
     */
    public int validate(boolean verbose) {
        printHeader();

        try {
            // Load migration spec
            spec = YamlUtils.loadYaml(specPath);

            if (compatMode) {
                System.out.println("[INFO] Running compat-mode validation checks (quarkus-spring-di)\n");
                checkCompatModeExtensionPresent();
                checkNoSpelValueAnnotations(); // @Value("${…}") bridged; @Value("#{…}") SpEL is not
                checkCompatTransactional(); // respects compat_mode.spring_tx flag
                checkCompatScheduled(); // respects compat_mode.spring_scheduled flag
                checkUnsupportedCompatAnnotations(); // @Primary, @Lazy, @Profile are NOT bridged
                checkMavenCompile();
            } else {
                System.out.println("[INFO] Running 18 validation checks for service layer migration\n");

                // Run all 18 checks
                checkNoSpringAnnotations();
                checkNoSpringImports();
                checkCdiScopesPresent();
                checkInjectUsage();
                checkConfigPropertyMigration();
                checkTransactionalMigration();
                checkAsyncMigration();
                checkScheduledMigration();
                checkNoApplicationContext();
                checkLifecycleHooks();
                checkNoSpringConfigClasses();
                checkNoSpringBeanMethods();
                checkEventPublisherMigration();
                checkNoSpringBootAnnotations();
                checkNoSpringContextUtils();
                checkJmsListenerMigration();
                checkScopeAnnotationMigration();
                checkMavenCompile();
            }

            // Print summary
            printSummary();

            // Save results
            saveResults();

            return report.getStatus().equals("success") ? 0 : 1;

        } catch (Exception e) {
            System.err.println("[ERROR] Validation failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private void checkCompatModeExtensionPresent() {
        String rule = "quarkus-spring-di extension present in pom.xml";
        System.out.println("[CHECK] " + rule);

        Path pomPath = targetDir.resolve("pom.xml");
        if (!pomPath.toFile().exists()) {
            report.fail(rule, "pom.xml not found in project root");
            return;
        }
        try {
            String pomContent = Files.readString(pomPath);
            if (pomContent.contains("quarkus-spring-di")) {
                report.pass(rule, "quarkus-spring-di found in pom.xml");
            } else {
                report.fail(rule, "quarkus-spring-di not found in pom.xml — add the dependency for compat mode");
            }
        } catch (IOException e) {
            report.fail(rule, "Error reading pom.xml: " + e.getMessage());
        }
    }

    private void checkCompatTransactional() {
        boolean springTxCompat = Boolean.TRUE.equals(
                YamlUtils.getValue(spec, "compat_mode.spring_tx"));

        if (springTxCompat) {
            // quarkus-spring-tx bridges Spring @Transactional — verify extension is present
            String rule = "quarkus-spring-tx extension present in pom.xml (compat_mode.spring_tx=true)";
            System.out.println("[CHECK] " + rule);
            try {
                String pomContent = Files.readString(targetDir.resolve("pom.xml"));
                if (pomContent.contains("quarkus-spring-tx")) {
                    report.pass(rule, "quarkus-spring-tx found in pom.xml");
                } else {
                    report.fail(rule,
                            "compat_mode.spring_tx is true but quarkus-spring-tx is not in pom.xml — add the extension or set spring_tx: false");
                }
            } catch (IOException e) {
                report.fail(rule, "Error reading pom.xml: " + e.getMessage());
            }
        } else {
            // Spring @Transactional import must be replaced with jakarta.transaction
            checkTransactionalMigration();
        }
    }

    private void checkCompatScheduled() {
        boolean springScheduledCompat = Boolean.TRUE.equals(
                YamlUtils.getValue(spec, "compat_mode.spring_scheduled"));

        if (springScheduledCompat) {
            // quarkus-spring-scheduled bridges @Scheduled — verify extension is present
            String rule = "quarkus-spring-scheduled extension present in pom.xml (compat_mode.spring_scheduled=true)";
            System.out.println("[CHECK] " + rule);
            try {
                String pomContent = Files.readString(targetDir.resolve("pom.xml"));
                if (pomContent.contains("quarkus-spring-scheduled")) {
                    report.pass(rule, "quarkus-spring-scheduled found in pom.xml");
                } else {
                    report.fail(rule,
                            "compat_mode.spring_scheduled is true but quarkus-spring-scheduled is not in pom.xml — add the extension or set spring_scheduled: false");
                }
            } catch (IOException e) {
                report.fail(rule, "Error reading pom.xml: " + e.getMessage());
            }
        } else {
            // Spring @Scheduled import must be replaced (or scheduling removed)
            checkScheduledMigration();
        }
    }

    private void checkUnsupportedCompatAnnotations() {
        String rule = "No unsupported quarkus-spring-di annotations (@Primary, @Lazy, @Profile) remain";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> unsupported = Arrays.asList("@Primary", "@Lazy", "@Profile");

            List<String> violations = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);
                for (String annotation : unsupported) {
                    if (activeContent.contains(annotation)) {
                        violations.add(file.getFileName().toString() + " (" + annotation + ")");
                    }
                }
            }

            if (violations.isEmpty()) {
                report.pass(rule, "No unsupported compat annotations found");
            } else {
                report.fail(rule, String.format(
                        "%d unsupported annotation(s) found — quarkus-spring-di silently ignores these causing runtime failures: %s. "
                                +
                                "Replace @Primary with @io.quarkus.arc.DefaultBean or @Alternative+@Priority; " +
                                "remove @Lazy (Quarkus beans are lazy by default); " +
                                "replace @Profile with @io.quarkus.arc.profile.IfBuildProfile.",
                        violations.size(), String.join(", ", violations.subList(0, Math.min(5, violations.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpringAnnotations() {
        String rule = "No Spring stereotype annotations remain";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> springAnnotations = Arrays.asList(
                    "@Service", "@Component", "@Repository",
                    "@Autowired", "@Value", "@Qualifier",
                    "@Primary", "@Lazy", "@DependsOn");

            List<String> badFiles = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                for (String annotation : springAnnotations) {
                    if (activeContent.contains(annotation)) {
                        badFiles.add(file.getFileName().toString());
                        break;
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "No Spring annotations found");
            } else {
                report.fail(rule, String.format("Spring annotations in %d files: %s",
                        badFiles.size(), String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpringImports() {
        String rule = "No Spring imports remain";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<Pattern> springImportPatterns = Arrays.asList(
                    Pattern.compile("import\\s+org\\.springframework\\.stereotype\\."),
                    Pattern.compile("import\\s+org\\.springframework\\.beans\\.factory\\.annotation\\."),
                    Pattern.compile("import\\s+org\\.springframework\\.context\\."),
                    Pattern.compile("import\\s+org\\.springframework\\.scheduling\\.annotation\\."),
                    Pattern.compile("import\\s+org\\.springframework\\.transaction\\.annotation\\.Transactional"));

            List<String> badFiles = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                for (Pattern pattern : springImportPatterns) {
                    if (pattern.matcher(activeContent).find()) {
                        badFiles.add(file.getFileName().toString());
                        break;
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "No Spring imports found");
            } else {
                report.fail(rule, String.format("Spring imports in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkCdiScopesPresent() {
        String rule = "CDI scope annotations present on service classes";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> cdiScopes = Arrays.asList(
                    "@ApplicationScoped", "@RequestScoped", "@SessionScoped",
                    "@Dependent", "@Singleton");

            List<String> badFiles = new ArrayList<>();
            Pattern serviceClassPattern = Pattern
                    .compile("public\\s+class\\s+\\w+(Service|Component|Repository|Manager|Handler|Processor)");
            Pattern interfacePattern = Pattern.compile("\\b(interface|enum|@interface)\\s+\\w+");

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                // Skip interfaces, enums, annotations
                if (interfacePattern.matcher(activeContent).find()) {
                    continue;
                }

                // Check if it's a service class
                if (serviceClassPattern.matcher(activeContent).find()) {
                    boolean hasScope = cdiScopes.stream().anyMatch(activeContent::contains);
                    if (!hasScope) {
                        badFiles.add(file.getFileName().toString());
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "All service classes have CDI scopes");
            } else {
                report.fail(rule, String.format("%d classes missing CDI scope", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkInjectUsage() {
        String rule = "@Inject used instead of @Autowired";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (activeContent.contains("@Autowired")) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "All @Autowired migrated to @Inject");
            } else {
                report.fail(rule, String.format("@Autowired in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpelValueAnnotations() {
        String rule = "No SpEL @Value(\"#{…}\") expressions remain (property placeholder @Value(\"${…}\") is bridged)";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            // Match @Value("#{...") — SpEL syntax; property placeholders @Value("${...")
            // are fine
            Pattern spelValuePattern = Pattern.compile("@Value\\s*\\(\\s*\"#\\{");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (spelValuePattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule,
                        "No SpEL @Value expressions found — property placeholder @Value is bridged by quarkus-spring-di");
            } else {
                report.fail(rule, String.format(
                        "SpEL @Value(\"#{…}\") in %d file(s): %s — SpEL is not supported by quarkus-spring-di; rewrite to a CDI equivalent.",
                        badFiles.size(), String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())))));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkConfigPropertyMigration() {
        String rule = "@Value migrated to @ConfigProperty";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern valuePattern = Pattern.compile("@Value\\s*\\(");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (valuePattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "All @Value migrated to @ConfigProperty");
            } else {
                report.fail(rule, String.format("@Value in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkTransactionalMigration() {
        String rule = "@Transactional uses jakarta.transaction";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern springTransactionalPattern = Pattern
                    .compile("import\\s+org\\.springframework\\.transaction\\.annotation\\.Transactional");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (springTransactionalPattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "@Transactional uses jakarta.transaction");
            } else {
                report.fail(rule, String.format("Spring @Transactional in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkAsyncMigration() {
        String rule = "@Async migrated to Uni or @Asynchronous";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (activeContent.contains("@Async") &&
                        activeContent.contains("org.springframework.scheduling.annotation.Async")) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "@Async migrated");
            } else {
                report.fail(rule, String.format("Spring @Async in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkScheduledMigration() {
        String rule = "@Scheduled migrated to Quarkus scheduler";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern springScheduledPattern = Pattern
                    .compile("import\\s+org\\.springframework\\.scheduling\\.annotation\\.Scheduled");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (springScheduledPattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "@Scheduled migrated");
            } else {
                report.fail(rule, String.format("Spring @Scheduled in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoApplicationContext() {
        String rule = "No ApplicationContext usage";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern applicationContextPattern = Pattern.compile("\\bApplicationContext\\b");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (applicationContextPattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "No ApplicationContext usage");
            } else {
                report.fail(rule, String.format("ApplicationContext in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkLifecycleHooks() {
        String rule = "Lifecycle hooks use jakarta.annotation";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern javaxAnnotationPattern = Pattern
                    .compile("import\\s+javax\\.annotation\\.(PostConstruct|PreDestroy)");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (javaxAnnotationPattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "Lifecycle hooks use jakarta.annotation");
            } else {
                report.fail(rule, String.format("javax.annotation in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpringConfigClasses() {
        String rule = "No Spring @Configuration classes remain";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> springConfigAnnotations = Arrays.asList(
                    "@Configuration", "@EnableAsync", "@EnableJms",
                    "@EnableScheduling", "@EnableTransactionManagement",
                    "@EnableConfigurationProperties", "@SpringBootApplication");

            List<String> badFiles = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                for (String annotation : springConfigAnnotations) {
                    if (activeContent.contains(annotation)) {
                        badFiles.add(file.getFileName().toString());
                        break;
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "No Spring config annotations found");
            } else {
                report.fail(rule, String.format("Spring config annotations in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpringBeanMethods() {
        String rule = "@Bean migrated to @Produces";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern beanPattern = Pattern.compile("@Bean\\s*(\\(|$)");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (beanPattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "All @Bean migrated to @Produces");
            } else {
                report.fail(rule, String.format("@Bean in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkEventPublisherMigration() {
        String rule = "ApplicationEventPublisher migrated to CDI events";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern eventPublisherPattern = Pattern.compile("\\bApplicationEventPublisher\\b");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (eventPublisherPattern.matcher(activeContent).find()) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "Event publishing migrated to CDI");
            } else {
                report.fail(rule, String.format("ApplicationEventPublisher in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpringBootAnnotations() {
        String rule = "No Spring Boot annotations remain";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<Pattern> springBootPatterns = Arrays.asList(
                    Pattern.compile("@SpringBootApplication"),
                    Pattern.compile("@EnableConfigurationProperties"),
                    Pattern.compile("@ConfigurationProperties"),
                    Pattern.compile("@ConditionalOn\\w+"),
                    Pattern.compile("@SpringBootTest"));

            List<String> badFiles = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                for (Pattern pattern : springBootPatterns) {
                    if (pattern.matcher(activeContent).find()) {
                        badFiles.add(file.getFileName().toString());
                        break;
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "No Spring Boot annotations found");
            } else {
                report.fail(rule, String.format("Spring Boot annotations in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkNoSpringContextUtils() {
        String rule = "No Spring context utilities remain";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            List<String> springUtils = Arrays.asList(
                    "SpringBeanAutowiringSupport",
                    "BeanFactoryUtils",
                    "ApplicationContextAware",
                    "BeanFactoryAware");

            List<String> badFiles = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                for (String util : springUtils) {
                    if (activeContent.contains(util)) {
                        badFiles.add(file.getFileName().toString());
                        break;
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "No Spring utilities found");
            } else {
                report.fail(rule, String.format("Spring utilities in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkJmsListenerMigration() {
        String rule = "@JmsListener migrated to @Incoming";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Pattern jmsListenerPattern = Pattern.compile("@JmsListener\\s*\\(");
            List<String> badFiles = new ArrayList<>();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                if (jmsListenerPattern.matcher(activeContent).find() &&
                        activeContent.contains("org.springframework.jms.annotation.JmsListener")) {
                    badFiles.add(file.getFileName().toString());
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "@JmsListener migrated");
            } else {
                report.fail(rule, String.format("Spring @JmsListener in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkScopeAnnotationMigration() {
        String rule = "Spring scope annotations migrated to CDI";
        System.out.println("[CHECK] " + rule);

        try {
            List<Path> javaFiles = findJavaFiles(targetDir);
            Map<String, String> springScopes = new HashMap<>();
            springScopes.put("@RequestScope", "@RequestScoped");
            springScopes.put("@SessionScope", "@SessionScoped");
            springScopes.put("@ApplicationScope", "@ApplicationScoped");

            List<String> badFiles = new ArrayList<>();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String activeContent = removeComments(content);

                for (Map.Entry<String, String> entry : springScopes.entrySet()) {
                    String springScope = entry.getKey();
                    Pattern pattern = Pattern.compile(Pattern.quote(springScope) + "\\b");

                    if (pattern.matcher(activeContent).find()) {
                        badFiles.add(file.getFileName().toString());
                        break;
                    }
                }
            }

            if (badFiles.isEmpty()) {
                report.pass(rule, "Scope annotations migrated");
            } else {
                report.fail(rule, String.format("Spring scopes in %d files", badFiles.size()));
            }
        } catch (IOException e) {
            report.fail(rule, "Error scanning files: " + e.getMessage());
        }
    }

    private void checkMavenCompile() {
        String rule = "Maven compile succeeds";
        System.out.println("[CHECK] " + rule);

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "compile", "-DskipTests");
            pb.directory(targetDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
                        .filter(line -> line.contains("ERROR"))
                        .limit(3)
                        .collect(Collectors.joining("\n  "));
                report.fail(rule, "Maven compile failed:\n  " + errorSnippet);
            }

        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program \"mvn\"")) {
                report.fail(rule, "Maven (mvn) not found in PATH");
            } else {
                report.fail(rule, "Error running Maven: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            report.fail(rule, "Maven process interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void printHeader() {
        System.out.println("======================================================================");
        System.out.println("ServiceValidator — service-layer migration (Spring to Quarkus CDI)");
        System.out.println("Target dir : " + targetDir);
        System.out.println("Spec file  : " + specPath);
        System.out.println("======================================================================");
        System.out.println();
    }

    private void printSummary() {
        System.out.println("\n======================================================================");
        System.out.println("Verification Summary — service-layer migration (Spring to Quarkus CDI)");
        System.out.println("======================================================================");
        System.out.println("Status  : " + report.getStatus().toUpperCase());
        System.out.println(String.format("Checks  : %d total  |  %d passed  |  %d failed\n",
                report.getTotal(), report.getPassed(), report.getFailed()));

        for (ValidationReport.Evidence evidence : report.getEvidenceList()) {
            String mark = evidence.isPassed() ? "✓" : "✗";
            System.out.println("  " + mark + " " + evidence.getRule());
            for (String line : evidence.getEvidence().split("\n")) {
                System.out.println("      " + line);
            }
        }
        System.out.println("======================================================================");
    }

    private void saveResults() {
        try {
            // Prepare entry for history
            Map<String, Object> entry = report.toMap("service-layer-migration");
            entry.put("migration_type", "spring-to-quarkus-cdi");

            // Ensure intermediate.history exists
            Map<String, Object> intermediate = (Map<String, Object>) spec.computeIfAbsent("intermediate",
                    k -> new LinkedHashMap<>());
            List<Map<String, Object>> history = (List<Map<String, Object>>) intermediate.computeIfAbsent("history",
                    k -> new ArrayList<>());
            history.add(entry);

            // Save with backup
            YamlUtils.saveYaml(specPath, spec);

            System.out.println("\nResults appended to : " + specPath);
            System.out.println("Backup written to   : "
                    + specPath.toAbsolutePath().getParent().resolve("migration-metadata/backups") + "/"
                    + specPath.getFileName() + ".bak.<timestamp>");

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to save results: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper methods
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
        content = content.replaceAll("//.*?$", "");
        // Remove multi-line comments
        content = content.replaceAll("/\\*.*?\\*/", "");
        return content;
    }
}

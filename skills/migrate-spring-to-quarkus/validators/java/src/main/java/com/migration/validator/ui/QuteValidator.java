package com.migration.validator.ui;

import com.migration.validator.core.ValidationReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * QuteValidator - Validates common Qute migration requirements
 * 
 * Checks:
 * - Qute dependency present (quarkus-rest-qute or quarkus-qute)
 * - Template structure (templates in src/main/resources/templates/)
 * - Static resources location (META-INF/resources/)
 * - No Spring Security CSRF tokens
 * - Qute configuration in application.properties
 * - Legacy dependencies removed
 * - Expression language migrated
 * - Source/target parity
 * - Quarkus build succeeds
 */
public class QuteValidator {

    private final Path targetDir;

    public QuteValidator(Path targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * Run all Qute validation checks
     */
    public void validate(ValidationReport report, Path sourceDir, UIMigrationType migrationType) throws IOException {
        validateQuteDependency(report);
        validateTemplateStructure(report);
        validateStaticResources(report);
        validateNoCsrfTokens(report);
        validateQuteConfiguration(report);
        validateLegacyDependenciesRemoved(migrationType, report);
        validateExpressionLanguageMigration(migrationType, report);
        validateSourceTargetParity(sourceDir, migrationType, report);
        validateQuarkusBuild(report);
    }

    /**
     * Check that quarkus-rest-qute or quarkus-qute dependency is present
     */
    public void validateQuteDependency(ValidationReport report) {
        System.out.println("[CHECK] quarkus_rest_qute_dependency...");

        String buildSystem = BuildSystemUtils.detectBuildSystem(targetDir);
        if ("unknown".equals(buildSystem)) {
            report.fail("quarkus_rest_qute_dependency",
                    "Build file not found (neither pom.xml nor build.gradle)");
            System.out.println("  ✗ Build file not found\n");
            return;
        }

        boolean hasRestQute = BuildSystemUtils.checkDependency(targetDir, "io.quarkus", "quarkus-rest-qute");
        boolean hasQute = BuildSystemUtils.checkDependency(targetDir, "io.quarkus", "quarkus-qute");

        if (!hasRestQute && !hasQute) {
            report.fail("quarkus_rest_qute_dependency",
                    "Neither quarkus-qute nor quarkus-rest-qute dependency found");
            System.out.println("  ✗ Missing Qute dependency\n");
            return;
        }

        if (hasQute && !hasRestQute) {
            report.pass("quarkus_rest_qute_dependency",
                    "quarkus-qute dependency present (valid for non-REST templates like mail/PDF). " +
                            "Consider quarkus-rest-qute if serving templates via REST endpoints");
            System.out.println("  ✓ quarkus-qute dependency present\n");
        } else {
            report.pass("quarkus_rest_qute_dependency", "quarkus-rest-qute dependency present");
            System.out.println("  ✓ quarkus-rest-qute dependency present\n");
        }
    }

    /**
     * Check that Qute templates are in correct directory structure
     */
    public void validateTemplateStructure(ValidationReport report) throws IOException {
        System.out.println("[CHECK] qute_template_structure...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.fail("qute_template_structure",
                    "templates directory not found at src/main/resources/templates");
            System.out.println("  ✗ templates directory not found\n");
            return;
        }

        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");

        if (htmlFiles.isEmpty()) {
            report.fail("qute_template_structure",
                    "No .html template files found in templates directory");
            System.out.println("  ✗ No templates found\n");
            return;
        }

        report.pass("qute_template_structure",
                "Found " + htmlFiles.size() + " Qute templates");
        System.out.println("  ✓ Found " + htmlFiles.size() + " Qute templates\n");
    }

    /**
     * Check that static resources are in META-INF/resources/
     */
    public void validateStaticResources(ValidationReport report) throws IOException {
        System.out.println("[CHECK] static_resources_location...");

        Path[] oldLocations = {
                targetDir.resolve("src/main/resources/static"),
                targetDir.resolve("src/main/webapp")
        };

        List<String> badFiles = new ArrayList<>();
        String[] resourceExtensions = { ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico" };

        for (Path oldLoc : oldLocations) {
            if (Files.exists(oldLoc)) {
                for (String ext : resourceExtensions) {
                    List<Path> files = FileUtils.findFilesByExtension(oldLoc, ext);
                    for (Path file : files) {
                        badFiles.add(targetDir.relativize(file).toString());
                    }
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("static_resources_location", "Static resources in correct location");
            System.out.println("  ✓ Static resources in correct location\n");
        } else {
            report.fail("static_resources_location",
                    "Found " + badFiles.size() + " resources in old locations (should be in META-INF/resources/): " +
                            String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size()))));
            System.out.println("  ✗ Found " + badFiles.size() + " resources in old locations\n");
        }
    }

    /**
     * Check that Spring Security CSRF tokens are removed
     */
    public void validateNoCsrfTokens(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_csrf_tokens...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        Path resourcesDir = targetDir.resolve("src/main/resources/META-INF/resources");

        List<String> badFiles = new ArrayList<>();
        Pattern[] csrfPatterns = {
                Pattern.compile("_csrf\\.token"),
                Pattern.compile("_csrf\\.parameterName"),
                Pattern.compile("_csrf\\.headerName"),
                Pattern.compile("name=\"_csrf\""),
                Pattern.compile("content=\"\\$\\{_csrf\\.token\\}\""),
                Pattern.compile("th:name=\"\\$\\{_csrf\\.parameterName\\}\""),
                Pattern.compile("<input[^>]*name=\"\\$\\{_csrf\\.parameterName\\}\"")
        };

        // Check HTML templates
        if (Files.exists(templatesDir)) {
            List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");
            for (Path htmlFile : htmlFiles) {
                String content = Files.readString(htmlFile);
                for (Pattern pattern : csrfPatterns) {
                    if (pattern.matcher(content).find()) {
                        badFiles.add(targetDir.relativize(htmlFile).toString());
                        break;
                    }
                }
            }
        }

        // Check JavaScript files
        if (Files.exists(resourcesDir)) {
            List<Path> jsFiles = FileUtils.findFilesByExtension(resourcesDir, ".js");
            for (Path jsFile : jsFiles) {
                String content = Files.readString(jsFile);
                if (content.toLowerCase().contains("_csrf")) {
                    badFiles.add(targetDir.relativize(jsFile).toString());
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_csrf_tokens", "No CSRF tokens found");
            System.out.println("  ✓ No CSRF tokens found\n");
        } else {
            report.fail("no_csrf_tokens",
                    "CSRF tokens found in " + badFiles.size() + " files: " +
                            String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size()))));
            System.out.println("  ✗ CSRF tokens in " + badFiles.size() + " files\n");
        }
    }

    /**
     * Check Qute configuration in application.properties or YAML files
     */
    public void validateQuteConfiguration(ValidationReport report) throws IOException {
        System.out.println("[CHECK] qute_configuration...");

        Path[] propsFiles = {
                targetDir.resolve("src/main/resources/application.properties"),
                targetDir.resolve("src/main/resources/application.yml"),
                targetDir.resolve("src/main/resources/application.yaml")
        };

        boolean configFound = false;
        boolean hasStrictRendering = false;
        boolean hasPropertyStrategy = false;

        for (Path propsFile : propsFiles) {
            if (Files.exists(propsFile)) {
                configFound = true;
                String content = Files.readString(propsFile);

                hasStrictRendering = content.contains("quarkus.qute.strict-rendering");
                hasPropertyStrategy = content.contains("quarkus.qute.property-not-found-strategy");
                break;
            }
        }

        if (!configFound) {
            report.pass("qute_configuration",
                    "No application.properties/yml found (configuration may be in other profiles)");
            System.out.println("  ✓ Configuration check skipped (no config file)\n");
            return;
        }

        StringBuilder evidence = new StringBuilder("Qute configuration present");
        if (!hasStrictRendering) {
            evidence.append(". Consider adding quarkus.qute.strict-rendering");
        }
        if (!hasPropertyStrategy) {
            evidence.append(". Consider adding quarkus.qute.property-not-found-strategy");
        }

        report.pass("qute_configuration", evidence.toString());
        System.out.println("  ✓ " + evidence + "\n");
    }

    /**
     * Check that legacy template engine dependencies are removed
     */
    public void validateLegacyDependenciesRemoved(UIMigrationType migrationType, ValidationReport report) {
        System.out.println("[CHECK] legacy_dependencies_removed...");

        String buildSystem = BuildSystemUtils.detectBuildSystem(targetDir);
        if ("unknown".equals(buildSystem)) {
            report.pass("legacy_dependencies_removed", "No build file to check");
            System.out.println("  ✓ No build file to check\n");
            return;
        }

        Map<String, List<String[]>> legacyDeps = new HashMap<>();
        legacyDeps.put("jsp-qute", Arrays.asList(
                new String[] { "javax.servlet", "jsp-api" },
                new String[] { "jakarta.servlet.jsp", "jakarta.servlet.jsp-api" },
                new String[] { "javax.servlet.jsp.jstl", "jstl" },
                new String[] { "jakarta.servlet.jsp.jstl", "jakarta.servlet.jsp.jstl-api" },
                new String[] { "org.apache.taglibs", "taglibs-standard" }));
        legacyDeps.put("thymeleaf-qute", Arrays.asList(
                new String[] { "org.thymeleaf", "thymeleaf" },
                new String[] { "org.thymeleaf", "thymeleaf-spring5" },
                new String[] { "org.thymeleaf", "thymeleaf-spring6" },
                new String[] { "io.quarkus", "quarkus-thymeleaf" }));
        legacyDeps.put("freemarker-qute", Arrays.asList(
                new String[] { "org.freemarker", "freemarker" },
                new String[] { "io.quarkus", "quarkus-freemarker" }));
        legacyDeps.put("jsf-qute", Arrays.asList(
                new String[] { "jakarta.faces", "jakarta.faces-api" },
                new String[] { "org.glassfish", "jakarta.faces" },
                new String[] { "org.apache.myfaces.core", "myfaces-api" },
                new String[] { "org.apache.myfaces.core", "myfaces-impl" },
                new String[] { "org.primefaces", "primefaces" },
                new String[] { "org.omnifaces", "omnifaces" }));

        List<String[]> depsToCheck = legacyDeps.getOrDefault(migrationType.getValue(), Collections.emptyList());
        List<String> badDeps = new ArrayList<>();

        for (String[] dep : depsToCheck) {
            if (BuildSystemUtils.checkDependency(targetDir, dep[0], dep[1])) {
                badDeps.add(dep[0] + ":" + dep[1]);
            }
        }

        if (badDeps.isEmpty()) {
            report.pass("legacy_dependencies_removed", "All legacy dependencies removed");
            System.out.println("  ✓ All legacy dependencies removed\n");
        } else {
            report.fail("legacy_dependencies_removed",
                    "Found " + badDeps.size() + " legacy dependencies: " + String.join(", ", badDeps));
            System.out.println("  ✗ Found " + badDeps.size() + " legacy dependencies\n");
        }
    }

    /**
     * Check that legacy expression language syntax is migrated
     */
    public void validateExpressionLanguageMigration(UIMigrationType migrationType, ValidationReport report)
            throws IOException {
        System.out.println("[CHECK] expression_language_migration...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.pass("expression_language_migration", "No templates directory");
            System.out.println("  ✓ No templates directory\n");
            return;
        }

        Map<String, List<Pattern>> elPatterns = new HashMap<>();
        elPatterns.put("jsp-qute", Arrays.asList(
                Pattern.compile("\\$\\{[^}]+\\}"), // JSP EL ${...}
                Pattern.compile("<c:") // JSTL tag
        ));
        elPatterns.put("thymeleaf-qute", Arrays.asList(
                Pattern.compile("\\$\\{[^}]+\\}"), // Thymeleaf ${...}
                Pattern.compile("\\*\\{[^}]+\\}"), // Thymeleaf *{...}
                Pattern.compile("@\\{[^}]+\\}"), // Thymeleaf @{...}
                Pattern.compile("#\\{[^}]+\\}") // Thymeleaf #{...}
        ));
        elPatterns.put("freemarker-qute", Arrays.asList(
                Pattern.compile("\\$\\{[^}]+\\}"), // FreeMarker ${...}
                Pattern.compile("<#") // FreeMarker directive
        ));
        elPatterns.put("jsf-qute", Arrays.asList(
                Pattern.compile("#\\{[^}]+\\}"), // JSF EL #{...}
                Pattern.compile("<[hfuip]:") // JSF component
        ));

        List<Pattern> patterns = elPatterns.getOrDefault(migrationType.getValue(), Collections.emptyList());
        List<String> badFiles = new ArrayList<>();

        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");
        for (Path htmlFile : htmlFiles) {
            String content = Files.readString(htmlFile);
            for (Pattern pattern : patterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(htmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("expression_language_migration", "Expression language migrated");
            System.out.println("  ✓ Expression language migrated\n");
        } else {
            report.fail("expression_language_migration",
                    "Legacy EL in " + badFiles.size() + " files: " +
                            String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size()))));
            System.out.println("  ✗ Legacy EL in " + badFiles.size() + " files\n");
        }
    }

    /**
     * Check that source and target have similar number of views
     */
    public void validateSourceTargetParity(Path sourceDir, UIMigrationType migrationType, ValidationReport report)
            throws IOException {
        System.out.println("[CHECK] source_target_parity...");

        Map<String, String[]> sourceExtensions = new HashMap<>();
        sourceExtensions.put("jsp-qute", new String[] { ".jsp", ".jspx" });
        sourceExtensions.put("thymeleaf-qute", new String[] { ".html" });
        sourceExtensions.put("freemarker-qute", new String[] { ".ftl", ".ftlh" });
        sourceExtensions.put("jsf-qute", new String[] { ".xhtml" });

        String[] sourceExts = sourceExtensions.getOrDefault(migrationType.getValue(), new String[0]);
        int sourceCount = 0;
        for (String ext : sourceExts) {
            sourceCount += FileUtils.findFilesByExtension(sourceDir, ext).size();
        }

        Path targetTemplates = targetDir.resolve("src/main/resources/templates");
        int targetCount = Files.exists(targetTemplates)
                ? FileUtils.findFilesByExtension(targetTemplates, ".html").size()
                : 0;

        // Allow some variance (fragments, includes may reduce count)
        if (targetCount < sourceCount * 0.5) {
            report.fail("source_target_parity",
                    "Significant view count difference: " + sourceCount + " source views → " +
                            targetCount + " target templates. Verify no migration loss.");
            System.out.println("  ✗ View count mismatch: " + sourceCount + " → " + targetCount + "\n");
        } else {
            report.pass("source_target_parity",
                    "View parity check: " + sourceCount + " source → " + targetCount + " target");
            System.out.println("  ✓ View parity: " + sourceCount + " → " + targetCount + "\n");
        }
    }

    /**
     * Check that Quarkus build succeeds (includes Qute template parsing)
     */
    public void validateQuarkusBuild(ValidationReport report) {
        System.out.println("[CHECK] quarkus_build...");

        String buildSystem = BuildSystemUtils.detectBuildSystem(targetDir);
        if ("unknown".equals(buildSystem)) {
            report.fail("quarkus_build", "Build file not found");
            System.out.println("  ✗ Build file not found\n");
            return;
        }

        try {
            List<String> cmd;
            if ("maven".equals(buildSystem)) {
                cmd = Arrays.asList("mvn", "clean", "quarkus:build", "-DskipTests");
            } else {
                Path gradlew = targetDir.resolve("gradlew");
                if (Files.exists(gradlew)) {
                    cmd = Arrays.asList("./gradlew", "clean", "build", "-x", "test");
                } else {
                    cmd = Arrays.asList("gradle", "clean", "build", "-x", "test");
                }
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(targetDir.toFile());
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

            boolean success = process.waitFor() == 0;

            if (success) {
                report.pass("quarkus_build", "Quarkus build successful (Qute templates validated)");
                System.out.println("  ✓ Quarkus build successful\n");
            } else {
                String evidence = output.toString();
                if (evidence.length() > 500) {
                    evidence = evidence.substring(0, 500) + "...";
                }
                report.fail("quarkus_build", "Quarkus build failed: " + evidence);
                System.out.println("  ✗ Quarkus build failed\n");
            }

        } catch (Exception e) {
            report.fail("quarkus_build", "Quarkus build error: " + e.getMessage());
            System.out.println("  ✗ Quarkus build error: " + e.getMessage() + "\n");
        }
    }

}

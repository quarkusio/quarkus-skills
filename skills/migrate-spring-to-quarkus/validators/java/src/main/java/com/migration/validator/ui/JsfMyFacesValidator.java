package com.migration.validator.ui;

import com.migration.validator.core.ValidationReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JsfMyFacesValidator - Validates JSF to Quarkus MyFaces migration (preserve
 * JSF)
 * 
 * Checks:
 * - MyFaces Quarkus extension present
 * - Jakarta Faces namespaces (not javax)
 * - faces-config.xml uses Jakarta
 * - CDI @Named beans (not @ManagedBean)
 * - Converters/validators use managed=true
 * - @ViewScoped beans implement Serializable
 * - XHTML files in META-INF/resources
 * - javax.* imports migrated to jakarta.*
 * - Source/target parity
 * - Build compile succeeds
 */
public class JsfMyFacesValidator {

    private final Path targetDir;

    public JsfMyFacesValidator(Path targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * Run all JSF MyFaces validation checks
     */
    public void validate(ValidationReport report, Path sourceDir) throws IOException {
        validateMyFacesDependency(report);
        validateJakartaFacesNamespaces(report);
        validateFacesConfigJakarta(report);
        validateCdiNamedBeans(report);
        validateConverterValidatorManaged(report);
        validateViewScopedSerializable(report);
        validateXhtmlResourcesLocation(report);
        validateJavaxJakartaImports(report);
        validateSourceTargetParity(sourceDir, report);
        validateBuildCompile(report);
    }

    /**
     * Check that Faces Quarkus extension is present
     */
    public void validateMyFacesDependency(ValidationReport report) {
        System.out.println("[CHECK] myfaces_dependency...");

        String buildSystem = BuildSystemUtils.detectBuildSystem(targetDir);
        if ("unknown".equals(buildSystem)) {
            report.fail("myfaces_dependency", "Build file not found");
            System.out.println("  ✗ Build file not found\n");
            return;
        }

        boolean hasMyFaces = BuildSystemUtils.checkDependency(targetDir, "org.apache.myfaces.core.extensions.quarkus",
                "myfaces-quarkus");
        boolean hasPrimeFaces = BuildSystemUtils.checkDependency(targetDir, "io.quarkiverse.primefaces",
                "quarkus-primefaces");

        if (!hasMyFaces && !hasPrimeFaces) {
            report.fail("myfaces_dependency",
                    "Faces Quarkus extension not found. Use either org.apache.myfaces.core.extensions.quarkus:myfaces-quarkus "
                            +
                            "or io.quarkiverse.primefaces:quarkus-primefaces");
            System.out.println("  ✗ Faces Quarkus extension not found\n");
        } else {
            report.pass("myfaces_dependency", "Faces Quarkus extension present");
            System.out.println("  ✓ Faces Quarkus extension present\n");
        }
    }

    /**
     * Check that XHTML files use Jakarta Faces namespaces
     */
    public void validateJakartaFacesNamespaces(ValidationReport report) throws IOException {
        System.out.println("[CHECK] jakarta_faces_namespaces...");

        List<Path> xhtmlFiles = FileUtils.findFilesByExtension(targetDir, ".xhtml");
        if (xhtmlFiles.isEmpty()) {
            report.pass("jakarta_faces_namespaces", "No XHTML files to check");
            System.out.println("  ✓ No XHTML files to check\n");
            return;
        }

        String[] oldNamespaces = {
                "xmlns.jcp.org/jsf",
                "java.sun.com/jsf",
                "xmlns:h=\"http://xmlns.jcp.org",
                "xmlns:f=\"http://xmlns.jcp.org",
                "xmlns:ui=\"http://xmlns.jcp.org"
        };

        List<String> badFiles = new ArrayList<>();

        for (Path xhtmlFile : xhtmlFiles) {
            String content = Files.readString(xhtmlFile);
            for (String oldNs : oldNamespaces) {
                if (content.contains(oldNs)) {
                    badFiles.add(targetDir.relativize(xhtmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("jakarta_faces_namespaces", "All XHTML files use Jakarta namespaces");
            System.out.println("  ✓ All XHTML files use Jakarta namespaces\n");
        } else {
            String evidence = "Old namespaces in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("jakarta_faces_namespaces", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that faces-config.xml uses Jakarta Faces 4.x schema
     */
    public void validateFacesConfigJakarta(ValidationReport report) throws IOException {
        System.out.println("[CHECK] faces_config_jakarta...");

        Path[] facesConfigPaths = {
                targetDir.resolve("src/main/resources/META-INF/faces-config.xml"),
                targetDir.resolve("src/main/webapp/WEB-INF/faces-config.xml")
        };

        boolean found = false;
        boolean passed = true;
        StringBuilder evidence = new StringBuilder();

        for (Path facesConfig : facesConfigPaths) {
            if (Files.exists(facesConfig)) {
                found = true;
                String content = Files.readString(facesConfig);

                // Check for Jakarta namespace
                if (!content.contains("https://jakarta.ee/xml/ns/jakartaee")) {
                    passed = false;
                    evidence.append("faces-config.xml does not use Jakarta EE namespace. ");
                }

                // Check for version 4.0
                if (!content.contains("version=\"4.0\"") && !content.contains("version=\"4.")) {
                    evidence.append("faces-config.xml should use version 4.0 or higher. ");
                }

                // Check for old namespace
                if (content.contains("java.sun.com") || content.contains("xmlns.jcp.org")) {
                    passed = false;
                    evidence.append("faces-config.xml contains old JSF namespaces. ");
                }

                break;
            }
        }

        if (!found) {
            report.pass("faces_config_jakarta", "No faces-config.xml found (may be using annotations)");
            System.out.println("  ✓ No faces-config.xml found\n");
        } else if (passed) {
            report.pass("faces_config_jakarta", "faces-config.xml uses Jakarta Faces 4.x");
            System.out.println("  ✓ faces-config.xml uses Jakarta Faces 4.x\n");
        } else {
            report.fail("faces_config_jakarta", evidence.toString().trim());
            System.out.println("  ✗ " + evidence.toString().trim() + "\n");
        }
    }

    /**
     * Check that JSF managed beans are converted to CDI @Named beans
     */
    public void validateCdiNamedBeans(ValidationReport report) throws IOException {
        System.out.println("[CHECK] cdi_named_beans...");

        List<Path> javaFiles = FileUtils.findFilesByExtension(targetDir, ".java");
        List<String> badFiles = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String activeContent = FileUtils.removeComments(content);

            // Check for old JSF managed bean annotations
            if (activeContent.contains("@ManagedBean")) {
                // Check if it has been converted to @Named
                if (!activeContent.contains("@Named")) {
                    badFiles.add(targetDir.relativize(javaFile).toString() + " (@ManagedBean not converted to @Named)");
                }
            }

            // Check for @ManagedProperty (should be @Inject)
            if (activeContent.contains("@ManagedProperty")) {
                badFiles.add(
                        targetDir.relativize(javaFile).toString() + " (@ManagedProperty not converted to @Inject)");
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("cdi_named_beans", "All beans use CDI annotations");
            System.out.println("  ✓ All beans use CDI annotations\n");
        } else {
            String evidence = "JSF managed beans in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("cdi_named_beans", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that converters and validators use managed=true for CDI
     */
    public void validateConverterValidatorManaged(ValidationReport report) throws IOException {
        System.out.println("[CHECK] converter_validator_managed...");

        List<Path> javaFiles = FileUtils.findFilesByExtension(targetDir, ".java");
        List<String> badFiles = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String activeContent = FileUtils.removeComments(content);

            // Check for @FacesConverter or @FacesValidator with @Inject
            boolean hasInject = activeContent.contains("@Inject");
            boolean hasConverter = activeContent.contains("@FacesConverter");
            boolean hasValidator = activeContent.contains("@FacesValidator");

            if (hasInject && (hasConverter || hasValidator)) {
                // Check if managed=true is present
                String annotation = hasConverter ? "@FacesConverter" : "@FacesValidator";
                Pattern managedPattern = Pattern.compile(annotation + "\\s*\\([^)]*managed\\s*=\\s*true");
                if (!managedPattern.matcher(activeContent).find()) {
                    badFiles.add(targetDir.relativize(javaFile).toString() +
                            " (" + annotation + " with @Inject must have managed=true)");
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("converter_validator_managed", "All converters/validators properly configured");
            System.out.println("  ✓ All converters/validators properly configured\n");
        } else {
            String evidence = "Issues in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("converter_validator_managed", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that @ViewScoped beans implement Serializable
     */
    public void validateViewScopedSerializable(ValidationReport report) throws IOException {
        System.out.println("[CHECK] viewscoped_serializable...");

        List<Path> javaFiles = FileUtils.findFilesByExtension(targetDir, ".java");
        List<String> badFiles = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String activeContent = FileUtils.removeComments(content);

            // Check for @ViewScoped
            if (activeContent.contains("@ViewScoped")) {
                // Check if implements Serializable
                Pattern serializablePattern = Pattern.compile("implements\\s+[^{]*\\bSerializable\\b", Pattern.DOTALL);
                if (!serializablePattern.matcher(activeContent).find()) {
                    badFiles.add(targetDir.relativize(javaFile).toString());
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("viewscoped_serializable", "All @ViewScoped beans implement Serializable");
            System.out.println("  ✓ All @ViewScoped beans implement Serializable\n");
        } else {
            String evidence = "Issues in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("viewscoped_serializable", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that XHTML files are in META-INF/resources
     */
    public void validateXhtmlResourcesLocation(ValidationReport report) throws IOException {
        System.out.println("[CHECK] xhtml_resources_location...");

        Path correctLocation = targetDir.resolve("src/main/resources/META-INF/resources");
        Path oldLocation = targetDir.resolve("src/main/webapp");

        List<String> badFiles = new ArrayList<>();
        List<String> goodFiles = new ArrayList<>();

        // Check for XHTML files in the correct location
        if (Files.exists(correctLocation)) {
            List<Path> correctXhtmlFiles = FileUtils.findFilesByExtension(correctLocation, ".xhtml");
            goodFiles = correctXhtmlFiles.stream()
                    .map(p -> targetDir.relativize(p).toString())
                    .collect(Collectors.toList());
        }

        // Check for XHTML files in the old location (error)
        if (Files.exists(oldLocation)) {
            List<Path> oldXhtmlFiles = FileUtils.findFilesByExtension(oldLocation, ".xhtml");
            badFiles = oldXhtmlFiles.stream()
                    .map(p -> targetDir.relativize(p).toString())
                    .collect(Collectors.toList());
        }

        if (badFiles.isEmpty() && !goodFiles.isEmpty()) {
            report.pass("xhtml_resources_location",
                    "XHTML files in correct location (" + goodFiles.size() + " files in META-INF/resources)");
            System.out.println("  ✓ XHTML files in correct location\n");
        } else if (badFiles.isEmpty() && goodFiles.isEmpty()) {
            report.pass("xhtml_resources_location", "No XHTML files found (may be expected)");
            System.out.println("  ✓ No XHTML files found\n");
        } else {
            String evidence = "Found " + badFiles.size() + " files in old location: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("xhtml_resources_location", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that javax.faces imports are migrated to jakarta.faces
     */
    public void validateJavaxJakartaImports(ValidationReport report) throws IOException {
        System.out.println("[CHECK] javax_jakarta_imports...");

        List<Path> javaFiles = FileUtils.findFilesByExtension(targetDir, ".java");
        List<String> badFiles = new ArrayList<>();

        Pattern[] javaxPatterns = {
                Pattern.compile("import\\s+javax\\.faces\\."),
                Pattern.compile("import\\s+javax\\.servlet\\.jsp\\.")
        };

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            for (Pattern pattern : javaxPatterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(javaFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("javax_jakarta_imports", "All imports use jakarta.*");
            System.out.println("  ✓ All imports use jakarta.*\n");
        } else {
            String evidence = "javax.* imports in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("javax_jakarta_imports", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that source and target have similar number of XHTML files
     */
    public void validateSourceTargetParity(Path sourceDir, ValidationReport report) throws IOException {
        System.out.println("[CHECK] source_target_parity...");

        int sourceCount = FileUtils.findFilesByExtension(sourceDir, ".xhtml").size();

        Path targetResources = targetDir.resolve("src/main/resources/META-INF/resources");
        int targetCount = Files.exists(targetResources)
                ? FileUtils.findFilesByExtension(targetResources, ".xhtml").size()
                : 0;

        // For JSF→JSF, we expect 1:1 parity
        if (targetCount < sourceCount) {
            int missingCount = sourceCount - targetCount;
            report.fail("source_target_parity",
                    "Missing XHTML files in target: " + sourceCount + " source files → " +
                            targetCount + " target files. " + missingCount + " files missing. " +
                            "All XHTML files must be migrated for JSF→MyFaces.");
            System.out.println("  ✗ Missing " + missingCount + " XHTML files\n");
        } else if (targetCount > sourceCount) {
            int extraCount = targetCount - sourceCount;
            report.pass("source_target_parity",
                    "More XHTML files in target than source: " + sourceCount + " source → " +
                            targetCount + " target. " + extraCount + " extra files found.");
            System.out.println("  ✓ XHTML parity: " + sourceCount + " → " + targetCount +
                    " (" + extraCount + " extra)\n");
        } else {
            report.pass("source_target_parity",
                    "XHTML file parity: " + sourceCount + " source → " + targetCount + " target");
            System.out.println("  ✓ XHTML parity: " + sourceCount + " → " + targetCount + "\n");
        }
    }

    /**
     * Check that build compile succeeds
     */
    public void validateBuildCompile(ValidationReport report) {
        System.out.println("[CHECK] build_compile...");

        String buildSystem = BuildSystemUtils.detectBuildSystem(targetDir);
        if ("unknown".equals(buildSystem)) {
            report.fail("build_compile", "Build file not found");
            System.out.println("  ✗ Build file not found\n");
            return;
        }

        try {
            List<String> cmd;
            String buildName;

            if ("maven".equals(buildSystem)) {
                cmd = Arrays.asList("mvn", "clean", "compile", "-DskipTests");
                buildName = "Maven";
            } else {
                Path gradlew = targetDir.resolve("gradlew");
                if (Files.exists(gradlew)) {
                    cmd = Arrays.asList("./gradlew", "clean", "compileJava");
                } else {
                    cmd = Arrays.asList("gradle", "clean", "compileJava");
                }
                buildName = "Gradle";
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
                report.pass("build_compile", buildName + " compile successful");
                System.out.println("  ✓ " + buildName + " compile successful\n");
            } else {
                String evidence = output.toString();
                if (evidence.length() > 500) {
                    evidence = evidence.substring(0, 500) + "...";
                }
                report.fail("build_compile", buildName + " compile failed: " + evidence);
                System.out.println("  ✗ " + buildName + " compile failed\n");
            }

        } catch (Exception e) {
            report.fail("build_compile", "Build compile error: " + e.getMessage());
            System.out.println("  ✗ Build compile error: " + e.getMessage() + "\n");
        }
    }

}

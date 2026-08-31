package com.migration.validator.ui;

import com.migration.validator.core.ValidationReport;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JsfRemovalValidator - Validates JSF to Qute migration
 * 
 * Checks:
 * - No JSF XHTML files remain
 * - No JSF components in templates
 * - No JSF managed beans
 */
public class JsfRemovalValidator {

    private final Path targetDir;

    public JsfRemovalValidator(Path targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * Run all JSF removal validation checks
     */
    public void validate(ValidationReport report) throws IOException {
        validateNoJsfXhtmlFiles(report);
        validateNoJsfComponents(report);
        validateNoJsfManagedBeans(report);
    }

    /**
     * Check that no JSF XHTML files remain (for JSF → Qute migration)
     */
    public void validateNoJsfXhtmlFiles(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_jsf_xhtml_files...");

        List<Path> xhtmlFiles = FileUtils.findFilesByExtension(targetDir, ".xhtml");

        if (xhtmlFiles.isEmpty()) {
            report.pass("no_jsf_xhtml_files", "No JSF XHTML files found");
            System.out.println("  ✓ No JSF XHTML files found\n");
        } else {
            List<String> fileNames = xhtmlFiles.stream()
                    .map(p -> targetDir.relativize(p).toString())
                    .limit(5)
                    .collect(Collectors.toList());

            String evidence = "Found " + xhtmlFiles.size() + " XHTML files: " +
                    String.join(", ", fileNames);
            if (xhtmlFiles.size() > 5) {
                evidence += " (and " + (xhtmlFiles.size() - 5) + " more)";
            }

            report.fail("no_jsf_xhtml_files", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that JSF components are removed from templates
     */
    public void validateNoJsfComponents(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_jsf_components...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.pass("no_jsf_components", "No templates directory");
            System.out.println("  ✓ No templates directory\n");
            return;
        }

        Pattern[] jsfPatterns = {
                Pattern.compile("<h:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<f:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<ui:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<p:", Pattern.CASE_INSENSITIVE), // PrimeFaces
                Pattern.compile("xmlns:h=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("xmlns:f=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("xmlns:ui=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("xmlns:p=", Pattern.CASE_INSENSITIVE)
        };

        List<String> badFiles = new ArrayList<>();
        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");

        for (Path htmlFile : htmlFiles) {
            String content = Files.readString(htmlFile);
            for (Pattern pattern : jsfPatterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(htmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_jsf_components", "No JSF components found");
            System.out.println("  ✓ No JSF components found\n");
        } else {
            String evidence = "JSF components in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("no_jsf_components", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that JSF managed beans are removed (for JSF → Qute migration)
     */
    public void validateNoJsfManagedBeans(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_jsf_managed_beans...");

        List<Path> javaFiles = FileUtils.findFilesByExtension(targetDir, ".java");
        List<String> badFiles = new ArrayList<>();

        String[] jsfAnnotations = {
                "@ManagedBean",
                "@ManagedProperty",
                "javax.faces.bean",
                "jakarta.faces.bean"
        };

        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String activeContent = FileUtils.removeComments(content);

            for (String annotation : jsfAnnotations) {
                if (activeContent.contains(annotation)) {
                    badFiles.add(targetDir.relativize(javaFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_jsf_managed_beans", "No JSF managed beans found");
            System.out.println("  ✓ No JSF managed beans found\n");
        } else {
            String evidence = "JSF managed beans in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("no_jsf_managed_beans", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

}

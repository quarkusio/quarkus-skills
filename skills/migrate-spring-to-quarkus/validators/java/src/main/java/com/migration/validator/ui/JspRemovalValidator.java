package com.migration.validator.ui;

import com.migration.validator.core.ValidationReport;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JspRemovalValidator - Validates JSP to Qute migration
 * 
 * Checks:
 * - No JSP files remain (.jsp, .jspx)
 * - No JSTL tags in templates
 * - No JSP implicit objects in templates
 */
public class JspRemovalValidator {

    private final Path targetDir;

    public JspRemovalValidator(Path targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * Run all JSP removal validation checks
     */
    public void validate(ValidationReport report) throws IOException {
        validateNoJspFiles(report);
        validateNoJstlTags(report);
        validateNoJspImplicitObjects(report);
    }

    /**
     * Check that no JSP files remain
     */
    public void validateNoJspFiles(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_jsp_files...");

        List<Path> jspFiles = new ArrayList<>();
        jspFiles.addAll(FileUtils.findFilesByExtension(targetDir, ".jsp"));
        jspFiles.addAll(FileUtils.findFilesByExtension(targetDir, ".jspx"));

        if (jspFiles.isEmpty()) {
            report.pass("no_jsp_files", "No JSP files found");
            System.out.println("  ✓ No JSP files found\n");
        } else {
            List<String> fileNames = jspFiles.stream()
                    .map(p -> targetDir.relativize(p).toString())
                    .limit(5)
                    .collect(Collectors.toList());

            String evidence = "Found " + jspFiles.size() + " JSP files: " +
                    String.join(", ", fileNames);
            if (jspFiles.size() > 5) {
                evidence += " (and " + (jspFiles.size() - 5) + " more)";
            }

            report.fail("no_jsp_files", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that JSTL tags are removed from templates
     */
    public void validateNoJstlTags(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_jstl_tags...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.pass("no_jstl_tags", "No templates directory");
            System.out.println("  ✓ No templates directory\n");
            return;
        }

        Pattern[] jstlPatterns = {
                Pattern.compile("<c:if", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<c:forEach", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<c:choose", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<c:when", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<c:otherwise", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<c:out", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<c:set", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<fmt:formatDate", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<fmt:formatNumber", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<fmt:message", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<%@\\s*taglib", Pattern.CASE_INSENSITIVE)
        };

        List<String> badFiles = new ArrayList<>();
        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");

        for (Path htmlFile : htmlFiles) {
            String content = Files.readString(htmlFile);
            for (Pattern pattern : jstlPatterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(htmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_jstl_tags", "No JSTL tags found");
            System.out.println("  ✓ No JSTL tags found\n");
        } else {
            String evidence = "JSTL tags in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("no_jstl_tags", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that JSP implicit objects are not used
     */
    public void validateNoJspImplicitObjects(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_jsp_implicit_objects...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.pass("no_jsp_implicit_objects", "No templates directory");
            System.out.println("  ✓ No templates directory\n");
            return;
        }

        Pattern[] implicitPatterns = {
                Pattern.compile("\\$\\{pageContext"),
                Pattern.compile("\\$\\{param\\."),
                Pattern.compile("\\$\\{header\\."),
                Pattern.compile("\\$\\{cookie\\."),
                Pattern.compile("\\$\\{sessionScope\\."),
                Pattern.compile("\\$\\{applicationScope\\.")
        };

        List<String> badFiles = new ArrayList<>();
        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");

        for (Path htmlFile : htmlFiles) {
            String content = Files.readString(htmlFile);
            for (Pattern pattern : implicitPatterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(htmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_jsp_implicit_objects", "No JSP implicit objects found");
            System.out.println("  ✓ No JSP implicit objects found\n");
        } else {
            String evidence = "JSP implicit objects in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("no_jsp_implicit_objects", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

}

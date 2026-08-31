package com.migration.validator.ui;

import com.migration.validator.core.ValidationReport;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * ThymeleafRemovalValidator - Validates Thymeleaf to Qute migration
 * 
 * Checks:
 * - No Thymeleaf attributes in templates (th:text, th:if, th:each, etc.)
 */
public class ThymeleafRemovalValidator {

    private final Path targetDir;

    public ThymeleafRemovalValidator(Path targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * Run all Thymeleaf removal validation checks
     */
    public void validate(ValidationReport report) throws IOException {
        validateNoThymeleafAttributes(report);
    }

    /**
     * Check that Thymeleaf attributes are removed
     */
    public void validateNoThymeleafAttributes(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_thymeleaf_attributes...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.pass("no_thymeleaf_attributes", "No templates directory");
            System.out.println("  ✓ No templates directory\n");
            return;
        }

        Pattern[] thymeleafPatterns = {
                Pattern.compile("\\sth:text=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:utext=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:if=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:unless=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:each=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:href=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:src=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:value=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:attr=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:object=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:field=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:errors=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:fragment=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:insert=", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\sth:replace=", Pattern.CASE_INSENSITIVE)
        };

        List<String> badFiles = new ArrayList<>();
        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");

        for (Path htmlFile : htmlFiles) {
            String content = Files.readString(htmlFile);
            for (Pattern pattern : thymeleafPatterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(htmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_thymeleaf_attributes", "No Thymeleaf attributes found");
            System.out.println("  ✓ No Thymeleaf attributes found\n");
        } else {
            String evidence = "Thymeleaf attributes in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("no_thymeleaf_attributes", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

}

package com.migration.validator.ui;

import com.migration.validator.core.ValidationReport;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * FreeMarkerRemovalValidator - Validates FreeMarker to Qute migration
 * 
 * Checks:
 * - No FreeMarker template files remain (.ftl, .ftlh, .ftlx)
 * - No FreeMarker directives in templates
 */
public class FreeMarkerRemovalValidator {

    private final Path targetDir;

    public FreeMarkerRemovalValidator(Path targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * Run all FreeMarker removal validation checks
     */
    public void validate(ValidationReport report) throws IOException {
        validateNoFreeMarkerFiles(report);
        validateNoFreeMarkerDirectives(report);
    }

    /**
     * Check that no FreeMarker template files remain
     */
    public void validateNoFreeMarkerFiles(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_freemarker_files...");

        List<Path> ftlFiles = new ArrayList<>();
        ftlFiles.addAll(FileUtils.findFilesByExtension(targetDir, ".ftl"));
        ftlFiles.addAll(FileUtils.findFilesByExtension(targetDir, ".ftlh"));
        ftlFiles.addAll(FileUtils.findFilesByExtension(targetDir, ".ftlx"));

        if (ftlFiles.isEmpty()) {
            report.pass("no_freemarker_files", "No FreeMarker files found");
            System.out.println("  ✓ No FreeMarker files found\n");
        } else {
            List<String> fileNames = ftlFiles.stream()
                    .map(p -> targetDir.relativize(p).toString())
                    .limit(5)
                    .collect(Collectors.toList());

            String evidence = "Found " + ftlFiles.size() + " FreeMarker files: " +
                    String.join(", ", fileNames);
            if (ftlFiles.size() > 5) {
                evidence += " (and " + (ftlFiles.size() - 5) + " more)";
            }

            report.fail("no_freemarker_files", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

    /**
     * Check that FreeMarker directives are removed from templates
     */
    public void validateNoFreeMarkerDirectives(ValidationReport report) throws IOException {
        System.out.println("[CHECK] no_freemarker_directives...");

        Path templatesDir = targetDir.resolve("src/main/resources/templates");
        if (!Files.exists(templatesDir)) {
            report.pass("no_freemarker_directives", "No templates directory");
            System.out.println("  ✓ No templates directory\n");
            return;
        }

        Pattern[] freeMarkerPatterns = {
                Pattern.compile("<#if\\s"),
                Pattern.compile("<#else>"),
                Pattern.compile("<#elseif\\s"),
                Pattern.compile("<#list\\s"),
                Pattern.compile("<#assign\\s"),
                Pattern.compile("<#local\\s"),
                Pattern.compile("<#global\\s"),
                Pattern.compile("<#include\\s"),
                Pattern.compile("<#import\\s"),
                Pattern.compile("<#macro\\s"),
                Pattern.compile("\\$\\{[^}]*\\?[^}]*\\}") // FreeMarker built-ins like ${var?upper_case}
        };

        List<String> badFiles = new ArrayList<>();
        List<Path> htmlFiles = FileUtils.findFilesByExtension(templatesDir, ".html");

        for (Path htmlFile : htmlFiles) {
            String content = Files.readString(htmlFile);
            for (Pattern pattern : freeMarkerPatterns) {
                if (pattern.matcher(content).find()) {
                    badFiles.add(targetDir.relativize(htmlFile).toString());
                    break;
                }
            }
        }

        if (badFiles.isEmpty()) {
            report.pass("no_freemarker_directives", "No FreeMarker directives found");
            System.out.println("  ✓ No FreeMarker directives found\n");
        } else {
            String evidence = "FreeMarker directives in " + badFiles.size() + " files: " +
                    String.join(", ", badFiles.subList(0, Math.min(3, badFiles.size())));
            report.fail("no_freemarker_directives", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }
    }

}

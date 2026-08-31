package com.migration.validator;

import com.migration.validator.core.ValidationReport;
import com.migration.validator.core.YamlUtils;
import com.migration.validator.ui.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * UIValidator - Main orchestrator for UI migration validation (Phase 8b)
 * Instance-based validator with constructor injection for better testability
 * and design.
 *
 * Validates UI layer migrations to Quarkus, supporting multiple migration
 * paths:
 * 1. JSP → Qute
 * 2. Thymeleaf → Qute
 * 3. FreeMarker → Qute
 * 4. JSF → Qute
 * 5. JSF → Quarkus MyFaces (maintaining JSF)
 *
 * Validation Categories:
 * - Template technology removal/migration
 * - Qute template structure and syntax
 * - Static resource migration
 * - Dependency verification
 * - Configuration checks
 * - Build verification
 */
public class UIValidator {

    private static final String PHASE = "phase_8b_ui_migration";

    private final Path sourceDir;
    private final Path targetDir;
    private final UIMigrationType migrationType;
    private final Path specPath;

    /**
     * Constructor with dependency injection.
     *
     * @param sourceDir        Absolute path to the Spring source project root
     * @param targetDir        Absolute path to the Quarkus target project root
     * @param migrationTypeStr Migration type string (jsp-qute, thymeleaf-qute,
     *                         etc.)
     * @param specPath         Absolute path to migration-spec.yaml
     * @throws IllegalArgumentException if migration type is invalid
     */
    public UIValidator(Path sourceDir, Path targetDir, String migrationTypeStr, Path specPath) {
        this.sourceDir = sourceDir.toAbsolutePath();
        this.targetDir = targetDir.toAbsolutePath();
        this.migrationType = UIMigrationType.fromValue(migrationTypeStr); // Validates automatically
        this.specPath = specPath.toAbsolutePath();
    }

    /**
     * Run validation and return exit code.
     *
     * @param verbose Enable verbose output with detailed logging
     * @return 0 for success, 1 for failure
     */
    public int validate(boolean verbose) {
        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("UI MIGRATION VALIDATION: " + migrationType.getDescription());
            System.out.println("=".repeat(60));
            System.out.println("Source: " + sourceDir);
            System.out.println("Target: " + targetDir);
            System.out.println("Type:   " + migrationType.getValue());
            System.out.println("=".repeat(60) + "\n");

            // Load existing spec
            Map<String, Object> spec = YamlUtils.loadYaml(specPath);

            // Run validation
            ValidationReport report = runValidation();

            // Save report to spec
            saveToSpec(spec, report);

            // Print summary
            report.printSummary(PHASE + " (" + migrationType.getValue() + ")");

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
     * Internal validation method - orchestrates all checks based on migration type
     */
    private ValidationReport runValidation() throws IOException {
        ValidationReport report = new ValidationReport();

        // Determine which validators to run based on migration type
        if (this.migrationType.isMyFacesMigration()) {
            // JSF to MyFaces: preserve JSF, validate MyFaces setup
            runJsfMyFacesValidation(this.sourceDir, this.targetDir, report);
        } else {
            // All Qute migrations: common Qute checks + technology-specific removal
            runQuteValidation(this.sourceDir, this.targetDir, this.migrationType, report);
        }

        return report;
    }

    /**
     * Run validation for Qute migrations (jsp-qute, thymeleaf-qute,
     * freemarker-qute, jsf-qute)
     */
    private void runQuteValidation(Path sourceDir, Path targetDir, UIMigrationType migrationType,
            ValidationReport report) throws IOException {
        // Common Qute checks
        QuteValidator quteValidator = new QuteValidator(targetDir);

        // Technology-specific removal checks
        switch (migrationType) {
            case JSP_QUTE:
                new JspRemovalValidator(targetDir).validate(report);
                break;

            case THYMELEAF_QUTE:
                new ThymeleafRemovalValidator(targetDir).validate(report);
                break;

            case FREEMARKER_QUTE:
                new FreeMarkerRemovalValidator(targetDir).validate(report);
                break;

            case JSF_QUTE:
                new JsfRemovalValidator(targetDir).validate(report);
                break;

            case JSF_MYFACES:
                // Should not reach here, but handle gracefully
                throw new IllegalStateException("JSF_MYFACES should use runJsfMyFacesValidation");
        }

        // Run all Qute validation checks (includes common checks and build
        // verification)
        quteValidator.validate(report, sourceDir, migrationType);
    }

    /**
     * Run validation for JSF to MyFaces migration (preserve JSF)
     */
    private void runJsfMyFacesValidation(Path sourceDir, Path targetDir,
            ValidationReport report) throws IOException {
        // Run all JSF MyFaces validation checks
        new JsfMyFacesValidator(targetDir).validate(report, sourceDir);
    }

    /**
     * Save validation report to migration-spec.yaml
     */
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

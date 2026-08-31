package com.migration.validator;

import com.migration.validator.core.*;
import com.migration.validator.model.PersistenceModels.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for Phase 5: Persistence Migration
 * Instance-based validator with constructor injection for better testability
 * and design.
 */
public class PersistenceValidator {

    private static final Map<String, String> DEFAULT_FETCH = Map.of(
            "ManyToOne", "EAGER",
            "OneToMany", "LAZY",
            "OneToOne", "EAGER",
            "ManyToMany", "LAZY");

    private static final String NOT_USED = "not used";

    private final Path springMetadataPath;
    private final Path quarkusMetadataPath;
    private final Path projectRoot;
    private final Path specPath;
    private final boolean compatMode;

    /**
     * Constructor with dependency injection (full migration mode).
     *
     * @param springMetadata  Path to Spring project code-metadata.yaml
     * @param quarkusMetadata Path to Quarkus project code-metadata.yaml
     * @param projectRoot     Absolute path to the target Quarkus project root
     * @param specPath        Absolute path to migration-spec.yaml
     */
    public PersistenceValidator(Path springMetadata, Path quarkusMetadata,
            Path projectRoot, Path specPath) {
        this(springMetadata, quarkusMetadata, projectRoot, specPath, false);
    }

    /**
     * Constructor with dependency injection.
     *
     * @param springMetadata  Path to Spring project code-metadata.yaml
     * @param quarkusMetadata Path to Quarkus project code-metadata.yaml
     * @param projectRoot     Absolute path to the target Quarkus project root
     * @param specPath        Absolute path to migration-spec.yaml
     * @param compatMode      When true, skips Panache checks and verifies
     *                        quarkus-spring-data-jpa instead
     */
    public PersistenceValidator(Path springMetadata, Path quarkusMetadata,
            Path projectRoot, Path specPath, boolean compatMode) {
        this.springMetadataPath = springMetadata.toAbsolutePath();
        this.quarkusMetadataPath = quarkusMetadata.toAbsolutePath();
        this.projectRoot = projectRoot.toAbsolutePath();
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
            System.out.println("\n" + "=".repeat(70));
            System.out.println("verify_persistence_migration.py — persistence phase (Spring to Quarkus)");
            System.out.println("Spring metadata  : " + springMetadataPath);
            System.out.println("Quarkus metadata : " + quarkusMetadataPath);
            System.out.println("Project root     : " + projectRoot);
            System.out.println("Spec file        : " + specPath);
            System.out.println("=".repeat(70) + "\n");

            // Load metadata files
            RepoMetadataModel springMetadata = loadMetadata(springMetadataPath);
            RepoMetadataModel quarkusMetadata = loadMetadata(quarkusMetadataPath);

            // Normalize entities (apply defaults)
            normalizeEntities(springMetadata.getEntities());
            normalizeEntities(quarkusMetadata.getEntities());

            // Load spec
            Map<String, Object> spec = YamlUtils.loadYaml(specPath);

            // Create validation report
            ValidationReport report = new ValidationReport();

            System.out.println("[INFO] Running validation rules for persistence-migration phase\n");
            if (compatMode) {
                System.out.println("[INFO] Mode: compat (quarkus-spring-data-jpa)\n");
            }

            // Run validation rules
            validateEntityCount(springMetadata, quarkusMetadata, report);
            validateEntities(springMetadata, quarkusMetadata, report);
            if (compatMode) {
                validateCompatModeExtensionPresent(report);
                validateCompatModeSpringTxPresent(spec, report);
                System.out.println(
                        "[INFO] Repository validation skipped (compat mode — JpaRepository interfaces preserved)\n");
            } else {
                validateRepositories(springMetadata, quarkusMetadata, report);
            }
            validatePersistenceConfig(springMetadata, quarkusMetadata, report);
            checkMavenCompile(report);

            // Print summary
            report.printSummary("persistence-migration (Spring to Quarkus)");

            // Save to spec
            saveToSpec(spec, report);

            System.out.println("\nResults appended to : " + specPath);
            System.out.println("Backup written to   : "
                    + specPath.toAbsolutePath().getParent().resolve("migration-metadata/backups") + "/"
                    + specPath.getFileName() + ".bak.<timestamp>");

            // Return exit code
            return report.hasFailures() ? 1 : 0;

        } catch (Exception e) {
            System.err.println("[ERROR] Validation failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private RepoMetadataModel loadMetadata(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        // Ignore unknown properties in YAML that aren't in our model
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(path.toFile(), RepoMetadataModel.class);
    }

    private void normalizeEntities(List<EntityModel> entities) {
        for (EntityModel entity : entities) {
            // Normalize relationships - apply default fetch strategies
            for (RelationshipModel rel : entity.getRelationships()) {
                if (rel.getFetch() == null) {
                    rel.setFetch(DEFAULT_FETCH.get(rel.getType()));
                }
            }

            // Normalize field columns to uppercase
            for (FieldModel field : entity.getFields()) {
                if (field.getColumn() != null) {
                    field.setColumn(field.getColumn().toUpperCase());
                }
            }

            // Normalize relationship columns to uppercase
            for (RelationshipModel rel : entity.getRelationships()) {
                if (rel.getColumn() != null) {
                    rel.setColumn(rel.getColumn().toUpperCase());
                }
            }
        }
    }

    private void validateEntityCount(RepoMetadataModel spring, RepoMetadataModel quarkus,
            ValidationReport report) {
        System.out.println("[RULE] Entity count matches between Spring and Quarkus");

        int springCount = spring.getEntities().size();
        int quarkusCount = quarkus.getEntities().size();

        if (springCount == quarkusCount) {
            report.pass("Entity count",
                    String.format("Entity count matches: %d entities in both projects", springCount));
            System.out.println(String.format("  ✓ Entity count matches: %d entities in both projects\n", springCount));
        } else {
            report.fail("Entity count",
                    String.format("Entity count mismatch: Spring has %d entities, Quarkus has %d entities",
                            springCount, quarkusCount));
            System.out.println(String.format("  ✗ Entity count mismatch: Spring has %d, Quarkus has %d\n",
                    springCount, quarkusCount));
        }
    }

    private void validateEntities(RepoMetadataModel spring, RepoMetadataModel quarkus,
            ValidationReport report) {
        // Build entity maps by class name
        Map<String, EntityModel> springEntities = spring.getEntities().stream()
                .collect(Collectors.toMap(EntityModel::getClassName, e -> e));
        Map<String, EntityModel> quarkusEntities = quarkus.getEntities().stream()
                .collect(Collectors.toMap(EntityModel::getClassName, e -> e));

        // Check for missing entities
        System.out.println("[RULE] All Spring entities exist in Quarkus");
        List<String> missingEntities = new ArrayList<>();
        for (String entityName : springEntities.keySet()) {
            if (!quarkusEntities.containsKey(entityName)) {
                missingEntities.add(entityName);
            }
        }

        if (missingEntities.isEmpty()) {
            report.pass("All entities migrated",
                    "All Spring entities found in Quarkus project");
            System.out.println("  ✓ All Spring entities found in Quarkus project\n");
        } else {
            String evidence = "Missing entities in Quarkus: " + String.join(", ", missingEntities);
            report.fail("All entities migrated", evidence);
            System.out.println("  ✗ " + evidence + "\n");
        }

        // Compare each entity
        for (String entityName : springEntities.keySet()) {
            if (quarkusEntities.containsKey(entityName)) {
                EntityModel springEntity = springEntities.get(entityName);
                EntityModel quarkusEntity = quarkusEntities.get(entityName);
                compareEntity(springEntity, quarkusEntity, report);
            }
        }

        // Check for new entities in Quarkus
        List<String> newEntities = new ArrayList<>();
        for (String entityName : quarkusEntities.keySet()) {
            if (!springEntities.containsKey(entityName)) {
                newEntities.add(entityName);
            }
        }

        if (!newEntities.isEmpty()) {
            System.out.println("[INFO] New entities in Quarkus: " + String.join(", ", newEntities));
        }
    }

    private void compareEntity(EntityModel spring, EntityModel quarkus, ValidationReport report) {
        System.out.println("[RULE] Validating entity: " + spring.getClassName());

        // Compare table name
        compareTableName(spring, quarkus, report);

        // Compare ID generation
        compareIdGeneration(spring, quarkus, report);

        // Compare fields
        compareFields(spring, quarkus, report);

        // Compare relationships
        compareRelationships(spring, quarkus, report);

        System.out.println();
    }

    private void compareTableName(EntityModel spring, EntityModel quarkus, ValidationReport report) {
        String springTable = spring.getTableName();
        String quarkusTable = quarkus.getTableName();

        if (Objects.equals(springTable, quarkusTable)) {
            System.out.println(String.format("  [DEBUG] Table name check: OK (%s)", springTable));
            report.pass(spring.getClassName() + " table name",
                    String.format("Table name %s validated successfully", springTable));
        } else {
            System.out.println(String.format("  [DEBUG] Table name check: MISMATCH (%s → %s)",
                    springTable, quarkusTable));

            // If Spring has no explicit @Table annotation (table_name is null),
            // allow different naming conventions in Quarkus (WARNING only)
            if (springTable == null) {
                report.pass(spring.getClassName() + " table name",
                        String.format(
                                "Table name differs: implicit '%s' → explicit '%s' (different naming conventions allowed)",
                                spring.getClassName(), quarkusTable));
            } else {
                report.fail(spring.getClassName() + " table name",
                        String.format("Table mismatch: %s → %s", springTable, quarkusTable));
            }
        }
    }

    private void compareIdGeneration(EntityModel spring, EntityModel quarkus, ValidationReport report) {
        IdGenerationModel springId = spring.getIdGeneration();
        IdGenerationModel quarkusId = quarkus.getIdGeneration();

        if (springId != null && quarkusId != null) {
            if (Objects.equals(springId.getStrategy(), quarkusId.getStrategy())) {
                System.out.println("  [DEBUG] ID generation check: OK");
                report.pass(spring.getClassName() + " ID generation",
                        String.format("ID generation strategy %s validated successfully", springId.getStrategy()));
            } else {
                System.out.println("  [DEBUG] ID generation check: MISMATCH");
                report.fail(spring.getClassName() + " ID generation",
                        String.format("ID generation strategy changed: %s → %s",
                                springId.getStrategy(), quarkusId.getStrategy()));
            }
        }
    }

    private void compareFields(EntityModel spring, EntityModel quarkus, ValidationReport report) {
        // Build field maps (exclude transient fields)
        Map<String, FieldModel> springFields = spring.getFields().stream()
                .filter(f -> !f.isTransientField())
                .collect(Collectors.toMap(FieldModel::getName, f -> f));
        Map<String, FieldModel> quarkusFields = quarkus.getFields().stream()
                .filter(f -> !f.isTransientField())
                .collect(Collectors.toMap(FieldModel::getName, f -> f));

        System.out.println(String.format("  [DEBUG] Comparing %d fields in %s",
                springFields.size(), spring.getClassName()));

        for (Map.Entry<String, FieldModel> entry : springFields.entrySet()) {
            String fieldName = entry.getKey();
            FieldModel springField = entry.getValue();

            if (!quarkusFields.containsKey(fieldName)) {
                System.out.println(String.format("  [DEBUG] Field check '%s': MISSING", fieldName));
                report.fail(spring.getClassName() + "." + fieldName,
                        String.format("Missing field %s", fieldName));
                continue;
            }

            FieldModel quarkusField = quarkusFields.get(fieldName);

            // Compare field type
            if (!Objects.equals(springField.getType(), quarkusField.getType())) {
                // Extract simple class names for comparison
                String springSimple = getSimpleClassName(springField.getType());
                String quarkusSimple = getSimpleClassName(quarkusField.getType());

                if (!springSimple.equals(quarkusSimple)) {
                    System.out.println(String.format("  [DEBUG] Field check '%s': TYPE MISMATCH", fieldName));
                    report.fail(spring.getClassName() + "." + fieldName,
                            String.format("Field type mismatch %s: %s → %s", fieldName, springSimple, quarkusSimple));
                } else {
                    // Package changed but type is the same - just info
                    System.out.println(String.format("  [DEBUG] Field check '%s': PACKAGE CHANGED", fieldName));
                }
                continue;
            }

            // Compare column name
            if (!Objects.equals(springField.getColumn(), quarkusField.getColumn())) {
                System.out.println(String.format("  [DEBUG] Field check '%s': COLUMN MISMATCH", fieldName));
                if (springField.getColumn() == null) {
                    // Spring had no explicit column, Quarkus does - this is OK
                    System.out.println(String.format("  [DEBUG] Column mapping added: %s → %s",
                            fieldName, quarkusField.getColumn()));
                } else {
                    report.fail(spring.getClassName() + "." + fieldName,
                            String.format("Column mismatch %s: %s → %s", fieldName,
                                    springField.getColumn(), quarkusField.getColumn()));
                }
                continue;
            }

            // Compare nullable constraint
            if (!Objects.equals(springField.getNullable(), quarkusField.getNullable())) {
                // Calculate effective values (JPA default: nullable=true)
                boolean springEffective = springField.getNullable() != null ? springField.getNullable() : true;
                boolean quarkusEffective = quarkusField.getNullable() != null ? quarkusField.getNullable() : true;

                if (springEffective != quarkusEffective) {
                    System.out.println(
                            String.format("  [DEBUG] Field check '%s': NULLABLE CONSTRAINT CHANGED", fieldName));
                    report.fail(spring.getClassName() + "." + fieldName,
                            String.format("Nullable constraint changed %s: %s → %s", fieldName,
                                    nullableStr(springField.getNullable()), nullableStr(quarkusField.getNullable())));
                    continue;
                }
            }

            // Compare unique constraint
            if (!Objects.equals(springField.getUnique(), quarkusField.getUnique())) {
                // Calculate effective values (JPA default: unique=false)
                boolean springEffective = springField.getUnique() != null ? springField.getUnique() : false;
                boolean quarkusEffective = quarkusField.getUnique() != null ? quarkusField.getUnique() : false;

                if (springEffective != quarkusEffective) {
                    System.out
                            .println(String.format("  [DEBUG] Field check '%s': UNIQUE CONSTRAINT CHANGED", fieldName));
                    report.fail(spring.getClassName() + "." + fieldName,
                            String.format("Unique constraint changed %s: %s → %s", fieldName,
                                    uniqueStr(springField.getUnique()), uniqueStr(quarkusField.getUnique())));
                    continue;
                }
            }

            // Field matches
            System.out.println(String.format("  [DEBUG] Field check '%s': OK", fieldName));
            report.pass(spring.getClassName() + "." + fieldName,
                    String.format("Field %s validated successfully", fieldName));
        }

        // Check for new fields in Quarkus
        for (String fieldName : quarkusFields.keySet()) {
            if (!springFields.containsKey(fieldName)) {
                System.out.println(String.format("  [DEBUG] New field detected: %s", fieldName));
            }
        }
    }

    private void compareRelationships(EntityModel spring, EntityModel quarkus, ValidationReport report) {
        // Build relationship maps by key (type, target, column)
        Map<String, RelationshipModel> springRels = spring.getRelationships().stream()
                .collect(Collectors.toMap(r -> relationshipKey(r), r -> r));
        Map<String, RelationshipModel> quarkusRels = quarkus.getRelationships().stream()
                .collect(Collectors.toMap(r -> relationshipKey(r), r -> r));

        for (Map.Entry<String, RelationshipModel> entry : springRels.entrySet()) {
            String key = entry.getKey();
            RelationshipModel springRel = entry.getValue();

            String column = springRel.getColumn() != null ? " mapped to `" + springRel.getColumn() + "`" : "";
            String relId = String.format("%s → %s%s", springRel.getType(), springRel.getTargetEntity(), column);

            if (!quarkusRels.containsKey(key)) {
                report.fail(spring.getClassName() + " relationship",
                        String.format("Missing relationship %s", relId));
                continue;
            }

            RelationshipModel quarkusRel = quarkusRels.get(key);
            boolean hasIssues = false;

            // Compare fetch strategy
            if (!Objects.equals(springRel.getFetch(), quarkusRel.getFetch())) {
                System.out.println(String.format("  [DEBUG] Fetch strategy changed: %s → %s",
                        springRel.getFetch(), quarkusRel.getFetch()));
                hasIssues = true;
            }

            // Compare mappedBy
            if (!Objects.equals(springRel.getMappedBy(), quarkusRel.getMappedBy())) {
                report.fail(spring.getClassName() + " relationship",
                        String.format("Mapped field name mismatch for %s: %s → %s", relId,
                                springRel.getMappedBy() != null ? springRel.getMappedBy() : NOT_USED,
                                quarkusRel.getMappedBy() != null ? quarkusRel.getMappedBy() : NOT_USED));
                hasIssues = true;
            }

            // Compare collection type
            if (!Objects.equals(springRel.getCollectionType(), quarkusRel.getCollectionType())) {
                report.fail(spring.getClassName() + " relationship",
                        String.format("Collection type mismatch for %s: %s → %s", relId,
                                springRel.getCollectionType() != null ? springRel.getCollectionType() : NOT_USED,
                                quarkusRel.getCollectionType() != null ? quarkusRel.getCollectionType() : NOT_USED));
                hasIssues = true;
            }

            // Compare cascade
            if (!Objects.equals(springRel.getCascade(), quarkusRel.getCascade())) {
                System.out.println(String.format("  [DEBUG] Cascade changed for %s", springRel.getTargetEntity()));
                hasIssues = true;
            }

            if (!hasIssues) {
                report.pass(spring.getClassName() + " relationship",
                        String.format("Relationship %s validated successfully", relId));
            }
        }
    }

    private void validateCompatModeExtensionPresent(ValidationReport report) {
        String rule = "quarkus-spring-data-jpa extension present in pom.xml";
        System.out.println("[RULE] " + rule);

        Path pomPath = projectRoot.resolve("pom.xml");
        if (!pomPath.toFile().exists()) {
            report.fail(rule, "pom.xml not found in project root");
            System.out.println("  ✗ pom.xml not found\n");
            return;
        }
        try {
            String pomContent = new String(java.nio.file.Files.readAllBytes(pomPath));
            if (pomContent.contains("quarkus-spring-data-jpa")) {
                report.pass(rule, "quarkus-spring-data-jpa found in pom.xml");
                System.out.println("  ✓ quarkus-spring-data-jpa found in pom.xml\n");
            } else {
                report.fail(rule, "quarkus-spring-data-jpa not found in pom.xml — add the dependency for compat mode");
                System.out.println("  ✗ quarkus-spring-data-jpa not found in pom.xml\n");
            }
        } catch (IOException e) {
            report.fail(rule, "Error reading pom.xml: " + e.getMessage());
            System.out.println("  ✗ Error reading pom.xml\n");
        }
    }

    /**
     * When compat_mode.spring_tx = true in the spec, verify that quarkus-spring-tx
     * is present
     * in pom.xml so that Spring @Transactional imports are bridged at runtime.
     */
    @SuppressWarnings("unchecked")
    private void validateCompatModeSpringTxPresent(Map<String, Object> spec, ValidationReport report) {
        // Read compat_mode.spring_tx from spec
        boolean springTxRequired = false;
        try {
            Object compatModeObj = spec.get("compat_mode");
            if (compatModeObj instanceof Map) {
                Object flag = ((Map<String, Object>) compatModeObj).get("spring_tx");
                springTxRequired = Boolean.TRUE.equals(flag);
            }
        } catch (Exception e) {
            // malformed spec — treat as not required
        }

        if (!springTxRequired) {
            System.out.println("[INFO] quarkus-spring-tx check skipped (compat_mode.spring_tx is not true in spec)\n");
            return;
        }

        String rule = "quarkus-spring-tx extension present in pom.xml (compat_mode.spring_tx=true)";
        System.out.println("[RULE] " + rule);

        Path pomPath = projectRoot.resolve("pom.xml");
        try {
            String pomContent = new String(java.nio.file.Files.readAllBytes(pomPath));
            if (pomContent.contains("quarkus-spring-tx")) {
                report.pass(rule, "quarkus-spring-tx found in pom.xml");
                System.out.println("  ✓ quarkus-spring-tx found in pom.xml\n");
            } else {
                report.fail(rule,
                        "quarkus-spring-tx not found in pom.xml — required because compat_mode.spring_tx=true. " +
                                "Either add the dependency or migrate @Transactional imports to jakarta.transaction.Transactional "
                                +
                                "and set compat_mode.spring_tx=false");
                System.out.println("  ✗ quarkus-spring-tx not found in pom.xml\n");
            }
        } catch (IOException e) {
            report.fail(rule, "Error reading pom.xml: " + e.getMessage());
            System.out.println("  ✗ Error reading pom.xml\n");
        }
    }

    private void validateRepositories(RepoMetadataModel spring, RepoMetadataModel quarkus,
            ValidationReport report) {
        System.out.println("[RULE] Repository count and structure");

        int springCount = spring.getRepositories().size();
        int quarkusCount = quarkus.getRepositories().size();

        if (springCount == quarkusCount) {
            report.pass("Repository count",
                    String.format("Repository count matches: %d repositories in both projects", springCount));
            System.out.println(String.format("  ✓ Repository count matches: %d repositories\n", springCount));
        } else {
            report.fail("Repository count",
                    String.format("Repository count mismatch: Spring has %d, Quarkus has %d",
                            springCount, quarkusCount));
            System.out.println(String.format("  ✗ Repository count mismatch: Spring has %d, Quarkus has %d\n",
                    springCount, quarkusCount));
        }
    }

    private void validatePersistenceConfig(RepoMetadataModel spring, RepoMetadataModel quarkus,
            ValidationReport report) {
        System.out.println("[RULE] Persistence configuration migrated");

        PersistenceConfigModel springConfig = spring.getProjectConfig() != null
                ? spring.getProjectConfig().getPersistenceConfig()
                : null;
        PersistenceConfigModel quarkusConfig = quarkus.getProjectConfig() != null
                ? quarkus.getProjectConfig().getPersistenceConfig()
                : null;

        if (springConfig == null || quarkusConfig == null) {
            report.pass("Persistence config", "Persistence configuration check skipped (metadata not available)");
            System.out.println("  ✓ Persistence configuration check skipped\n");
            return;
        }

        // Check datasource configuration
        boolean hasDataSource = quarkusConfig.getDatasourceUrl() != null &&
                quarkusConfig.getDatasourceDriver() != null;

        if (hasDataSource) {
            report.pass("Persistence config", "Datasource configuration present in Quarkus");
            System.out.println("  ✓ Datasource configuration present in Quarkus\n");
        } else {
            report.fail("Persistence config", "Datasource configuration missing in Quarkus");
            System.out.println("  ✗ Datasource configuration missing in Quarkus\n");
        }
    }

    private void checkMavenCompile(ValidationReport report) {
        System.out.println("[RULE] mvn compile succeeds without errors");
        System.out.println("[DEBUG] Running mvn compile...");
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-B", "--no-transfer-progress");
            pb.directory(this.projectRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder outputBuilder = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                report.fail("mvn compile", "Maven compile timed out after 180 seconds");
                System.out.println("  ✗ Maven compile timed out after 180 seconds\n");
                return;
            }

            int exitCode = process.exitValue();
            String output = outputBuilder.toString();
            boolean success = exitCode == 0 && output.toUpperCase().contains("BUILD SUCCESS");

            if (success) {
                report.pass("mvn compile", "mvn compile — BUILD SUCCESS");
                System.out.println("  ✓ mvn compile — BUILD SUCCESS\n");
            } else {
                // Extract error lines
                String[] lines = output.split("\n");
                String errors = Arrays.stream(lines)
                        .filter(l -> l.contains("[ERROR]"))
                        .limit(5)
                        .collect(Collectors.joining("\n  "));

                String evidence = "mvn compile — BUILD FAILURE (exit=" + exitCode + ")\n  " +
                        (errors.isEmpty() ? output.substring(Math.max(0, output.length() - 400)) : errors);
                report.fail("mvn compile", evidence);
                System.out.println("  ✗ " + evidence + "\n");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            report.fail("mvn compile", "Maven compile interrupted");
            System.out.println("  ✗ Maven compile interrupted\n");
        } catch (IOException e) {
            report.fail("mvn compile", "Maven (mvn) not found in PATH — ensure mvn is installed and on PATH");
            System.out.println("  ✗ Maven (mvn) not found in PATH\n");
        }
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
            history.add(report.toMap("persistence-migration"));

            // Save updated spec
            YamlUtils.saveYaml(this.specPath, spec);

        } catch (IOException e) {
            System.err.println("[WARNING] Could not save to spec: " + e.getMessage());
        }
    }

    // Helper methods

    private static String getSimpleClassName(String fullClassName) {
        if (fullClassName == null)
            return null;
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private static String relationshipKey(RelationshipModel rel) {
        String target = getSimpleClassName(rel.getTargetEntity());
        return String.format("%s|%s|%s", rel.getType(), target, rel.getColumn());
    }

    private static String nullableStr(Boolean nullable) {
        if (nullable == null)
            return "nullable (implicit)";
        return nullable ? "nullable" : "not null";
    }

    private static String uniqueStr(Boolean unique) {
        if (unique == null)
            return "not unique (implicit)";
        return unique ? "unique" : "not unique";
    }
}

package com.migration.validator.cli.commands;

import com.migration.validator.PersistenceValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 5: Persistence Migration.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate persistence \
 * <spring-metadata.yaml> <quarkus-metadata.yaml> <project_root>
 * <migration-spec.yaml> [--verbose]
 */
@Command(name = "persistence", description = "Validate Phase 5: Persistence Migration - Verifies JPA entity migration from Spring Data to Quarkus Panache/Hibernate ORM", mixinStandardHelpOptions = true)
public class PersistenceCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Spring project code-metadata.yaml file")
    private Path springMetadata;

    @Parameters(index = "1", description = "Quarkus project code-metadata.yaml file")
    private Path quarkusMetadata;

    @Parameters(index = "2", description = "Target Quarkus project root directory")
    private Path projectRoot;

    @Parameters(index = "3", description = "Path to migration-spec.yaml file")
    private Path specPath;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Option(names = "--compat-mode", description = "Compat mode: skip Panache-specific checks, verify quarkus-spring-data-jpa extension instead", defaultValue = "false")
    private boolean compatMode;

    @Override
    public Integer call() throws Exception {
        PersistenceValidator validator = new PersistenceValidator(
                springMetadata, quarkusMetadata, projectRoot, specPath, compatMode);
        return validator.validate(verbose);
    }
}

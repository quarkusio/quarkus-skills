package com.migration.validator.cli.commands;

import com.migration.validator.DatabaseMigrationValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 4: Database Migration.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate database \
 * <project_root> <migration-spec.yaml> [--verbose]
 */
@Command(name = "database", description = "Validate Phase 4: Database Migration - Verifies database configuration, import.sql, and JDBC setup", mixinStandardHelpOptions = true)
public class DatabaseCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target Quarkus project root directory")
    private Path projectRoot;

    @Parameters(index = "1", description = "Path to migration-spec.yaml file")
    private Path specPath;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        DatabaseMigrationValidator validator = new DatabaseMigrationValidator(projectRoot, specPath);
        return validator.validate(verbose);
    }
}

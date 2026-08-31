package com.migration.validator.cli.commands;

import com.migration.validator.RestValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 8: Web Layer Migration.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate rest \
 * <spring-metadata.yaml> <quarkus-metadata.yaml> <project_root>
 * <migration-spec.yaml> [--verbose]
 */
@Command(name = "rest", description = "Validate Phase 8: Web Layer Migration - Verifies REST endpoint migration from Spring MVC to JAX-RS", mixinStandardHelpOptions = true)
public class RestCommand implements Callable<Integer> {

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

    @Option(names = "--compat-mode", description = "Compat mode: use SpringRestApiExtractor for target project (quarkus-spring-web); endpoint comparison logic is unchanged", defaultValue = "false")
    private boolean compatMode;

    @Override
    public Integer call() throws Exception {
        RestValidator validator = new RestValidator(
                springMetadata, quarkusMetadata, projectRoot, specPath, compatMode);
        return validator.validate(verbose);
    }
}

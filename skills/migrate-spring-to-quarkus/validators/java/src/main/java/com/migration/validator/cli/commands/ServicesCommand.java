package com.migration.validator.cli.commands;

import com.migration.validator.ServiceValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 6: Service Layer Migration.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate services \
 * <target_dir> <migration-spec.yaml> [--verbose]
 */
@Command(name = "services", description = "Validate Phase 6: Service Layer Migration - Verifies Spring @Service/@Component migration to CDI beans. "
        +
        "In --compat-mode: checks quarkus-spring-di extension present, @Value migrated, " +
        "@Transactional import replaced (or quarkus-spring-tx present when compat_mode.spring_tx=true), " +
        "Spring @Scheduled import replaced (or quarkus-spring-scheduled present when compat_mode.spring_scheduled=true), "
        +
        "and that unsupported annotations (@Primary, @Lazy, @Profile) have been removed.", mixinStandardHelpOptions = true)
public class ServicesCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target Quarkus project directory")
    private Path targetDir;

    @Parameters(index = "1", description = "Path to migration-spec.yaml file")
    private Path specPath;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Option(names = "--compat-mode", description = "Compat mode: allow Spring DI annotations, verify quarkus-spring-di extension instead", defaultValue = "false")
    private boolean compatMode;

    @Override
    public Integer call() throws Exception {
        ServiceValidator validator = new ServiceValidator(targetDir, specPath, compatMode);
        return validator.validate(verbose);
    }
}

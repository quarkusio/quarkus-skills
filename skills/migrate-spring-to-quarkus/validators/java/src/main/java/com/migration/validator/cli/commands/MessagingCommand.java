package com.migration.validator.cli.commands;

import com.migration.validator.MessagingValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 7: Messaging Migration.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate messaging \
 * <spring-metadata.json> <quarkus-metadata.json> <project_dir>
 * <migration-spec.yaml> [--verbose]
 */
@Command(name = "messaging", description = "Validate Phase 7: Messaging Migration - Verifies Kafka/JMS/RabbitMQ migration to SmallRye Reactive Messaging", mixinStandardHelpOptions = true)
public class MessagingCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Spring messaging metadata JSON file")
    private Path springMetadata;

    @Parameters(index = "1", description = "Quarkus messaging metadata JSON file")
    private Path quarkusMetadata;

    @Parameters(index = "2", description = "Target Quarkus project directory")
    private Path projectDir;

    @Parameters(index = "3", description = "Path to migration-spec.yaml file")
    private Path specPath;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        MessagingValidator validator = new MessagingValidator(
                springMetadata, quarkusMetadata, projectDir, specPath);
        return validator.validate(verbose);
    }
}

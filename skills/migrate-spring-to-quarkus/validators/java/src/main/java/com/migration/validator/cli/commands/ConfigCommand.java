package com.migration.validator.cli.commands;

import com.migration.validator.ConfigValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 9: Configuration Migration.
 *
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate config \
 * <spring_project> <quarkus_project> <migration-spec.yaml> [--verbose]
 *
 * Compat mode (service_layer = spring-di-compat or config_properties =
 * spring-boot-properties-compat):
 * java -jar migration-validator-1.0.0.jar validate config \
 * <spring_project> <quarkus_project> <migration-spec.yaml> --compat-mode
 *
 * In --compat-mode the validator checks:
 * - quarkus-spring-di present in pom.xml (when service_layer =
 * spring-di-compat)
 * - No RestTemplate @Bean methods remain (not bridged by quarkus-spring-di)
 * - No SpEL @Value("#{…}") expressions remain (property
 * placeholder @Value("${…}") is bridged)
 * - quarkus-spring-boot-properties present (when config_properties =
 * spring-boot-properties-compat)
 * - No Map<K,V> fields in @ConfigurationProperties classes (throws
 * DeploymentException at startup)
 * - No @ConstructorBinding remains (compat requires no-arg constructor +
 * setters)
 * - Property migration and no-Spring-properties checks (always apply)
 * - Maven compile succeeds
 */
@Command(name = "config", description = "Validate Phase 9: Configuration Migration - Verifies application.properties migration from Spring Boot to Quarkus. "
        +
        "In --compat-mode: checks quarkus-spring-di / quarkus-spring-boot-properties extensions present, " +
        "no RestTemplate @Bean methods remain, no SpEL @Value(\"#{…}\") remains, " +
        "no Map<K,V> fields in @ConfigurationProperties (when spring-boot-properties-compat), " +
        "and no @ConstructorBinding remains (when spring-boot-properties-compat).", mixinStandardHelpOptions = true)
public class ConfigCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Spring project root directory")
    private Path springProject;

    @Parameters(index = "1", description = "Quarkus project root directory")
    private Path quarkusProject;

    @Parameters(index = "2", description = "Path to migration-spec.yaml file")
    private Path specPath;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Option(names = "--compat-mode", description = "Compat mode: allow @Configuration/@Bean and @ConfigurationProperties to remain; "
            +
            "verify compat extensions present and check for unsupported patterns instead", defaultValue = "false")
    private boolean compatMode;

    @Override
    public Integer call() throws Exception {
        ConfigValidator validator = new ConfigValidator(springProject, quarkusProject, specPath, compatMode);
        return validator.validate(verbose);
    }
}

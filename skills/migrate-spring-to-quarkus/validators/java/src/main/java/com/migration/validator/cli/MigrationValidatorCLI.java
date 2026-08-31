package com.migration.validator.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main entry point for the Migration Validator CLI.
 *
 * Usage:
 * java -jar migration-validator-1.0.0.jar --help
 * java -jar migration-validator-1.0.0.jar validate <subcommand> [options]
 * <args>
 * java -jar migration-validator-1.0.0.jar extract <subcommand> [options] <args>
 */
@Command(name = "migration-validator", description = "Spring to Quarkus Migration Validator", mixinStandardHelpOptions = true, version = "1.0.0", subcommands = {
        ValidateCommand.class,
        ExtractCommand.class
})
public class MigrationValidatorCLI implements Runnable {

    @Override
    public void run() {
        // Show help when no subcommand is provided
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MigrationValidatorCLI()).execute(args);
        System.exit(exitCode);
    }
}

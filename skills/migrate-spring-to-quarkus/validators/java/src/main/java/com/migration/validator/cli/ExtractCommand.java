package com.migration.validator.cli;

import com.migration.validator.cli.commands.extract.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Extract command with subcommands for metadata extraction.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar extract --help
 * java -jar migration-validator-1.0.0.jar extract spring-messaging
 * <project_root> <output_file>
 * java -jar migration-validator-1.0.0.jar extract quarkus-messaging
 * <project_root> <output_file>
 * java -jar migration-validator-1.0.0.jar extract server-config <project_root>
 * <output_file>
 * java -jar migration-validator-1.0.0.jar extract metadata <project_root>
 * [options]
 */
@Command(name = "extract", description = "Extract metadata from Spring or Quarkus projects", mixinStandardHelpOptions = true, subcommands = {
        SpringMessagingCommand.class,
        QuarkusMessagingCommand.class,
        ServerConfigCommand.class,
        MetadataCommand.class
})
public class ExtractCommand implements Runnable {

    @Override
    public void run() {
        // Show help when no subcommand is provided
        CommandLine.usage(this, System.out);
    }
}

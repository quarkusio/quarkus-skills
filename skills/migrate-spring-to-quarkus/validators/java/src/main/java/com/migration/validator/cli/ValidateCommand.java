package com.migration.validator.cli;

import com.migration.validator.cli.commands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Validate command with subcommands for each migration phase.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate --help
 * java -jar migration-validator-1.0.0.jar validate project-setup <args>
 * java -jar migration-validator-1.0.0.jar validate persistence <args>
 */
@Command(name = "validate", description = "Validate migration phases", mixinStandardHelpOptions = true, subcommands = {
        ProjectSetupCommand.class,
        PersistenceCommand.class,
        ServicesCommand.class,
        RestCommand.class,
        MessagingCommand.class,
        DatabaseCommand.class,
        ConfigCommand.class,
        UICommand.class
})
public class ValidateCommand implements Runnable {

    @Override
    public void run() {
        // Show help when no subcommand is provided
        CommandLine.usage(this, System.out);
    }
}

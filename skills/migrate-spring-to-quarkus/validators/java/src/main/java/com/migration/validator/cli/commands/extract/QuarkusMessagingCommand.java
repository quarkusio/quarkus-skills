package com.migration.validator.cli.commands.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.extractor.QuarkusMessagingExtractor;
import com.migration.validator.model.QuarkusMessagingModels.QuarkusMessagingMetadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * CLI command to extract Quarkus messaging metadata.
 * 
 * Detects messaging through:
 * - @Incoming, @Outgoing (SmallRye Reactive Messaging)
 * - @Channel annotations
 * - application.properties messaging configuration
 * - Kafka, AMQP, JMS connectors
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar extract quarkus-messaging
 * <project_root> <output_file>
 * 
 * Example:
 * java -jar migration-validator-1.0.0.jar extract quarkus-messaging \
 * /path/to/quarkus-project \
 * quarkus-messaging.json
 */
@Command(name = "quarkus-messaging", description = "Extract Quarkus messaging metadata (SmallRye Reactive Messaging)", mixinStandardHelpOptions = true)
public class QuarkusMessagingCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Quarkus project root directory")
    private String projectRoot;

    @Parameters(index = "1", description = "Output JSON file path")
    private String outputFile;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        try {
            System.out.println("=" + "=".repeat(79));
            System.out.println("QUARKUS MESSAGING METADATA EXTRACTOR");
            System.out.println("=" + "=".repeat(79));
            System.out.println("Project Root: " + projectRoot);
            System.out.println("Output File: " + outputFile + "\n");

            // Extract metadata
            QuarkusMessagingExtractor extractor = new QuarkusMessagingExtractor(projectRoot);
            QuarkusMessagingMetadata metadata = extractor.extractAll();

            // Write to JSON file
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(outputFile), metadata);

            System.out.println("\n📄 Metadata exported to: " + outputFile);
            System.out.println("\n" + "=".repeat(80));

            return 0;

        } catch (Exception e) {
            System.err.println("❌ Error extracting Quarkus messaging metadata: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}

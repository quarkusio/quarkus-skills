package com.migration.validator.cli.commands.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.extractor.SpringMessagingExtractor;
import com.migration.validator.model.SpringMessagingModels.SpringMessagingMetadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * CLI command to extract Spring messaging metadata.
 * 
 * Detects messaging through:
 * - @KafkaListener, @RabbitListener, @JmsListener
 * - KafkaTemplate, RabbitTemplate, JmsTemplate
 * - @StreamListener, @EnableBinding (Spring Cloud Stream)
 * - application.properties/yml configuration
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar extract spring-messaging
 * <project_root> <output_file>
 * 
 * Example:
 * java -jar migration-validator-1.0.0.jar extract spring-messaging \
 * /path/to/spring-project \
 * spring-messaging.json
 */
@Command(name = "spring-messaging", description = "Extract Spring messaging metadata (Kafka, RabbitMQ, JMS, Spring Cloud Stream)", mixinStandardHelpOptions = true)
public class SpringMessagingCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Spring project root directory")
    private String projectRoot;

    @Parameters(index = "1", description = "Output JSON file path")
    private String outputFile;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        try {
            System.out.println("=" + "=".repeat(79));
            System.out.println("SPRING MESSAGING METADATA EXTRACTOR");
            System.out.println("=" + "=".repeat(79));
            System.out.println("Project Root: " + projectRoot);
            System.out.println("Output File: " + outputFile + "\n");

            // Extract metadata
            SpringMessagingExtractor extractor = new SpringMessagingExtractor(projectRoot);
            SpringMessagingMetadata metadata = extractor.extractAll();

            // Write to JSON file
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(outputFile), metadata);

            System.out.println("\n📄 Metadata exported to: " + outputFile);
            System.out.println("\n" + "=".repeat(80));

            return 0;

        } catch (Exception e) {
            System.err.println("❌ Error extracting Spring messaging metadata: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}

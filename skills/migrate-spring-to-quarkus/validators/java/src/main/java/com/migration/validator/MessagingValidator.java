package com.migration.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.validator.core.ValidationReport;
import com.migration.validator.core.YamlUtils;
import com.migration.validator.model.MessagingModels.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * MessagingValidator - Validates messaging migration from Spring to Quarkus
 * (Phase 7)
 * Instance-based validator with constructor injection for better testability
 * and design.
 *
 * Validates:
 * 1. Destination Coverage: All Spring destinations have Quarkus channel
 * equivalents
 * 2. Consumer Mapping: All @KafkaListener/@JmsListener map to @Incoming
 * 3. Producer Mapping: All Template usage maps to @Outgoing or Emitters
 * 4. Message Types: Message types are preserved in migration
 * 5. Configuration: Messaging channels are configured
 * 6. No Orphaned Channels: All channels have consumers or producers
 * 7. Maven compile succeeds
 */
public class MessagingValidator {

    private static final String PHASE = "phase_7_messaging";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path springMetadataPath;
    private final Path quarkusMetadataPath;
    private final Path projectDir;
    private final Path specPath;

    /**
     * Constructor with dependency injection.
     *
     * @param springMetadata  Path to Spring messaging metadata JSON
     * @param quarkusMetadata Path to Quarkus messaging metadata JSON
     * @param projectDir      Absolute path to the target Quarkus project root
     * @param specPath        Absolute path to migration-spec.yaml
     */
    public MessagingValidator(Path springMetadata, Path quarkusMetadata,
            Path projectDir, Path specPath) {
        this.springMetadataPath = springMetadata.toAbsolutePath();
        this.quarkusMetadataPath = quarkusMetadata.toAbsolutePath();
        this.projectDir = projectDir.toAbsolutePath();
        this.specPath = specPath.toAbsolutePath();
    }

    /**
     * Run validation and return exit code.
     *
     * @param verbose Enable verbose output with detailed logging
     * @return 0 for success, 1 for failure
     */
    public int validate(boolean verbose) {
        try {
            ValidationReport report = runValidation();

            // Load existing spec
            Map<String, Object> spec = YamlUtils.loadYaml(specPath);

            // Save report to spec
            saveToSpec(spec, report);

            // Print summary
            report.printSummary(PHASE);

            // Return exit code
            return report.hasFailures() ? 1 : 0;

        } catch (Exception e) {
            System.err.println("Validation failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * Internal validation method
     */
    private ValidationReport runValidation() throws IOException {
        ValidationReport report = new ValidationReport();

        // Check if metadata files exist
        if (!Files.exists(springMetadataPath)) {
            report.fail("metadata_check",
                    "Spring messaging metadata not found: " + springMetadataPath +
                            "\nGenerate using: python3 spring_messaging_extractor.py <spring-project> "
                            + springMetadataPath);
            return report;
        }

        if (!Files.exists(quarkusMetadataPath)) {
            report.fail("metadata_check",
                    "Quarkus messaging metadata not found: " + quarkusMetadataPath +
                            "\nGenerate using: python3 quarkus_messaging_extractor.py <quarkus-project> "
                            + quarkusMetadataPath);
            return report;
        }

        // Load metadata
        SpringMessagingMetadata springMetadata = loadSpringMetadata(springMetadataPath);
        QuarkusMessagingMetadata quarkusMetadata = loadQuarkusMetadata(quarkusMetadataPath);

        // Check if there's any messaging to validate
        if (isEmpty(springMetadata)) {
            report.pass("metadata_check", "No Spring messaging found - skipping messaging validation");
            return report;
        }

        report.pass("metadata_check", "Metadata files loaded successfully");

        // Run validation checks
        validateDestinationCoverage(springMetadata, quarkusMetadata, report);
        validateConsumerMapping(springMetadata, quarkusMetadata, report);
        validateProducerMapping(springMetadata, quarkusMetadata, report);
        validateMessageTypes(springMetadata, quarkusMetadata, report);
        validateConfiguration(quarkusMetadata, report);
        validateNoOrphanedChannels(quarkusMetadata, report);
        validateMavenCompile(report);

        return report;
    }

    /**
     * Load Spring messaging metadata
     */
    private SpringMessagingMetadata loadSpringMetadata(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), SpringMessagingMetadata.class);
    }

    /**
     * Load Quarkus messaging metadata
     */
    private QuarkusMessagingMetadata loadQuarkusMetadata(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), QuarkusMessagingMetadata.class);
    }

    /**
     * Check if Spring metadata is empty
     */
    private boolean isEmpty(SpringMessagingMetadata metadata) {
        return (metadata.destinations == null || metadata.destinations.isEmpty()) &&
                (metadata.messageListeners == null || metadata.messageListeners.isEmpty()) &&
                (metadata.messageProducers == null || metadata.messageProducers.isEmpty());
    }

    /**
     * Validate that all Spring destinations have Quarkus channel equivalents
     */
    private void validateDestinationCoverage(SpringMessagingMetadata spring, QuarkusMessagingMetadata quarkus,
            ValidationReport report) {
        List<SpringDestination> destinations = spring.destinations != null ? spring.destinations : new ArrayList<>();
        List<QuarkusMessagingChannel> channels = getChannels(quarkus);

        if (destinations.isEmpty()) {
            report.pass("destination_coverage", "No Spring destinations to migrate");
            return;
        }

        List<String> uncovered = new ArrayList<>();
        for (SpringDestination dest : destinations) {
            boolean found = false;
            for (QuarkusMessagingChannel channel : channels) {
                if (destinationMatchesChannel(dest, channel)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                uncovered.add(dest.name + " (" + dest.type + ")");
            }
        }

        if (uncovered.isEmpty()) {
            report.pass("destination_coverage",
                    String.format("All %d Spring destinations have Quarkus channel equivalents", destinations.size()));
        } else {
            report.fail("destination_coverage",
                    String.format("%d/%d destinations not covered:\n  - %s",
                            uncovered.size(), destinations.size(), String.join("\n  - ", uncovered)));
        }
    }

    /**
     * Validate that all Spring listeners map to @Incoming methods
     */
    private void validateConsumerMapping(SpringMessagingMetadata spring, QuarkusMessagingMetadata quarkus,
            ValidationReport report) {
        List<SpringMessageListener> listeners = spring.messageListeners != null ? spring.messageListeners
                : new ArrayList<>();
        List<QuarkusMessageConsumer> consumers = quarkus.messageConsumers != null ? quarkus.messageConsumers
                : new ArrayList<>();

        if (listeners.isEmpty()) {
            report.pass("consumer_mapping", "No Spring message listeners to migrate");
            return;
        }

        List<String> unmapped = new ArrayList<>();
        for (SpringMessageListener listener : listeners) {
            boolean found = false;
            for (QuarkusMessageConsumer consumer : consumers) {
                if (consumerMapsToListener(consumer, listener)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                unmapped.add(String.format("%s.%s (@%s on %s)",
                        listener.className, listener.methodName, listener.listenerType, listener.destinationName));
            }
        }

        if (unmapped.isEmpty()) {
            report.pass("consumer_mapping",
                    String.format("All %d Spring listeners map to @Incoming methods", listeners.size()));
        } else {
            report.fail("consumer_mapping",
                    String.format("%d/%d listeners not mapped:\n  - %s",
                            unmapped.size(), listeners.size(), String.join("\n  - ", unmapped)));
        }
    }

    /**
     * Validate that all Spring producers map to @Outgoing or Emitter
     */
    private void validateProducerMapping(SpringMessagingMetadata spring, QuarkusMessagingMetadata quarkus,
            ValidationReport report) {
        List<SpringMessageProducer> springProducers = spring.messageProducers != null ? spring.messageProducers
                : new ArrayList<>();
        List<QuarkusMessageProducer> quarkusProducers = quarkus.messageProducers != null ? quarkus.messageProducers
                : new ArrayList<>();

        if (springProducers.isEmpty()) {
            report.pass("producer_mapping", "No Spring message producers to migrate");
            return;
        }

        List<String> unmapped = new ArrayList<>();
        for (SpringMessageProducer springProd : springProducers) {
            boolean found = false;
            for (QuarkusMessageProducer quarkusProd : quarkusProducers) {
                if (producerMaps(springProd, quarkusProd)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                unmapped.add(String.format("%s.%s (%s to %s)",
                        springProd.className, springProd.methodName, springProd.producerType,
                        springProd.destinationVariable));
            }
        }

        if (unmapped.isEmpty()) {
            report.pass("producer_mapping",
                    String.format("All %d Spring producers map to Quarkus producers", springProducers.size()));
        } else {
            report.fail("producer_mapping",
                    String.format("%d/%d producers not mapped:\n  - %s",
                            unmapped.size(), springProducers.size(), String.join("\n  - ", unmapped)));
        }
    }

    /**
     * Validate that message types are preserved
     */
    private void validateMessageTypes(SpringMessagingMetadata spring, QuarkusMessagingMetadata quarkus,
            ValidationReport report) {
        List<SpringMessageProducer> springProducers = spring.messageProducers != null ? spring.messageProducers
                : new ArrayList<>();
        List<QuarkusMessageProducer> quarkusProducers = quarkus.messageProducers != null ? quarkus.messageProducers
                : new ArrayList<>();

        List<String> mismatches = new ArrayList<>();
        for (SpringMessageProducer springProd : springProducers) {
            if (springProd.messageType == null || springProd.messageType.equals("Unknown")) {
                continue;
            }

            String prodKey = springProd.className + "." + springProd.methodName;
            for (QuarkusMessageProducer quarkusProd : quarkusProducers) {
                String quarkusKey = quarkusProd.className + "." + quarkusProd.methodName;
                if (prodKey.equals(quarkusKey)) {
                    if (!typesCompatible(springProd.messageType, quarkusProd.messageType)) {
                        mismatches.add(String.format("%s: %s → %s",
                                prodKey, springProd.messageType, quarkusProd.messageType));
                    }
                    break;
                }
            }
        }

        if (mismatches.isEmpty()) {
            report.pass("message_types", "All message types are preserved in migration");
        } else {
            report.fail("message_types",
                    String.format("Message type mismatches:\n  - %s", String.join("\n  - ", mismatches)));
        }
    }

    /**
     * Validate that messaging channels are configured
     */
    private void validateConfiguration(QuarkusMessagingMetadata quarkus, ValidationReport report) {
        List<QuarkusMessagingChannel> channels = getChannels(quarkus);

        if (channels.isEmpty()) {
            report.fail("configuration", "No Quarkus messaging channels configured");
        } else {
            report.pass("configuration",
                    String.format("Found %d configured messaging channels", channels.size()));
        }
    }

    /**
     * Validate that there are no orphaned channels
     */
    private void validateNoOrphanedChannels(QuarkusMessagingMetadata quarkus, ValidationReport report) {
        List<QuarkusMessagingChannel> channels = getChannels(quarkus);
        List<QuarkusMessageConsumer> consumers = quarkus.messageConsumers != null ? quarkus.messageConsumers
                : new ArrayList<>();
        List<QuarkusMessageProducer> producers = quarkus.messageProducers != null ? quarkus.messageProducers
                : new ArrayList<>();

        if (channels.isEmpty()) {
            return; // Already reported in configuration check
        }

        Map<String, Integer> channelUsage = new HashMap<>();
        for (QuarkusMessagingChannel channel : channels) {
            channelUsage.put(channel.name, 0);
        }

        for (QuarkusMessageConsumer consumer : consumers) {
            if (channelUsage.containsKey(consumer.channelName)) {
                channelUsage.put(consumer.channelName, channelUsage.get(consumer.channelName) + 1);
            }
        }

        for (QuarkusMessageProducer producer : producers) {
            if (channelUsage.containsKey(producer.channelName)) {
                channelUsage.put(producer.channelName, channelUsage.get(producer.channelName) + 1);
            }
        }

        List<String> orphaned = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : channelUsage.entrySet()) {
            if (entry.getValue() == 0) {
                orphaned.add(entry.getKey());
            }
        }

        if (orphaned.isEmpty()) {
            report.pass("orphaned_channels", "All messaging channels are utilized");
        } else {
            report.fail("orphaned_channels",
                    String.format("Found %d orphaned channels:\n  - %s",
                            orphaned.size(), String.join("\n  - ", orphaned)));
        }
    }

    /**
     * Validate Maven compile succeeds
     */
    private void validateMavenCompile(ValidationReport report) {
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "compile", "-q");
            pb.directory(this.projectDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                report.pass("maven_compile", "Maven compile successful");
            } else {
                report.fail("maven_compile", "Maven compile failed. Fix compilation errors.");
            }

        } catch (Exception e) {
            report.fail("maven_compile",
                    "Maven compile check failed: " + e.getMessage() +
                            ". Ensure Maven is installed and project is valid.");
        }
    }

    // Helper methods

    /**
     * Get channels from Quarkus metadata (handles both field names)
     */
    private List<QuarkusMessagingChannel> getChannels(QuarkusMessagingMetadata quarkus) {
        if (quarkus.messagingChannels != null && !quarkus.messagingChannels.isEmpty()) {
            return quarkus.messagingChannels;
        }
        if (quarkus.channels != null && !quarkus.channels.isEmpty()) {
            return quarkus.channels;
        }
        return new ArrayList<>();
    }

    /**
     * Check if a Spring destination matches a Quarkus channel
     */
    private boolean destinationMatchesChannel(SpringDestination dest, QuarkusMessagingChannel channel) {
        String destName = normalizeMessagingName(dest.name);
        String channelName = normalizeMessagingName(channel.name);
        String topicOrQueue = normalizeMessagingName(channel.topicOrQueue);

        return destName.equals(channelName) ||
                destName.equals(topicOrQueue) ||
                destName.contains(channelName) ||
                channelName.contains(destName) ||
                (topicOrQueue != null && (destName.contains(topicOrQueue) || topicOrQueue.contains(destName)));
    }

    /**
     * Check if a Quarkus consumer maps to a Spring listener
     */
    private boolean consumerMapsToListener(QuarkusMessageConsumer consumer, SpringMessageListener listener) {
        // Check class and method name match
        String consumerKey = consumer.className + "." + consumer.methodName;
        String listenerKey = listener.className + "." + listener.methodName;

        if (consumerKey.equals(listenerKey)) {
            return true;
        }

        // Check destination/channel name match
        String listenerDest = normalizeMessagingName(listener.destinationName);
        String consumerChannel = normalizeMessagingName(consumer.channelName);

        return listenerDest.equals(consumerChannel) ||
                listenerDest.contains(consumerChannel) ||
                consumerChannel.contains(listenerDest);
    }

    /**
     * Check if a Quarkus producer maps to a Spring producer
     */
    private boolean producerMaps(SpringMessageProducer springProd, QuarkusMessageProducer quarkusProd) {
        // Check class and method name match
        String springKey = springProd.className + "." + springProd.methodName;
        String quarkusKey = quarkusProd.className + "." + quarkusProd.methodName;

        if (springKey.equals(quarkusKey)) {
            return true;
        }

        // Check destination/channel name match
        String springDest = normalizeMessagingName(springProd.destinationVariable);
        String quarkusChannel = normalizeMessagingName(quarkusProd.channelName);

        return springDest.equals(quarkusChannel) ||
                springDest.contains(quarkusChannel) ||
                quarkusChannel.contains(springDest);
    }

    /**
     * Check if message types are compatible
     */
    private boolean typesCompatible(String springType, String quarkusType) {
        if (springType == null || quarkusType == null) {
            return true; // Can't validate
        }

        String spring = springType.toLowerCase().replace("message", "");
        String quarkus = quarkusType.toLowerCase().replace("message", "");

        // Exact match
        if (spring.equals(quarkus)) {
            return true;
        }

        // Text types
        String[] textTypes = { "text", "string", "str" };
        boolean springIsText = false;
        boolean quarkusIsText = false;
        for (String type : textTypes) {
            if (spring.contains(type))
                springIsText = true;
            if (quarkus.contains(type))
                quarkusIsText = true;
        }
        if (springIsText && quarkusIsText) {
            return true;
        }

        // Object types
        String[] objectTypes = { "object", "serializable" };
        boolean springIsObject = false;
        boolean quarkusIsObject = false;
        for (String type : objectTypes) {
            if (spring.contains(type))
                springIsObject = true;
            if (quarkus.contains(type))
                quarkusIsObject = true;
        }
        if (springIsObject && quarkusIsObject) {
            return true;
        }

        return false;
    }

    /**
     * Normalize messaging names for comparison
     */
    private String normalizeMessagingName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
                .replace("-", "")
                .replace("_", "")
                .replace(".", "")
                .trim();
    }

    @SuppressWarnings("unchecked")
    private void saveToSpec(Map<String, Object> spec, ValidationReport report) {
        try {
            // Ensure intermediate.history exists
            Map<String, Object> intermediate = (Map<String, Object>) spec.computeIfAbsent("intermediate",
                    k -> new HashMap<>());
            List<Map<String, Object>> history = (List<Map<String, Object>>) intermediate.computeIfAbsent("history",
                    k -> new ArrayList<>());

            // Add this validation run to history
            history.add(report.toMap(PHASE));

            // Save updated spec
            YamlUtils.saveYaml(this.specPath, spec);

        } catch (IOException e) {
            System.err.println("[WARNING] Could not save to spec: " + e.getMessage());
        }
    }
}

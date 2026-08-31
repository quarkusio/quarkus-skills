package com.migration.validator.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.model.QuarkusMessagingModels.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Quarkus Messaging Metadata Extractor
 * 
 * Detects messaging through:
 * - Reactive Messaging annotations (@Incoming, @Outgoing, @Channel)
 * - Kafka connectors and configuration
 * - AMQP/RabbitMQ connectors
 * - JMS/Artemis integration
 * - Vert.x Event Bus
 * - Emitters and programmatic message sending
 * - Configuration files (application.properties, application.yml)
 */
public class QuarkusMessagingExtractor {

    private final Path rootDir;
    private final List<MessagingChannel> channels = new ArrayList<>();
    private final List<MessageConsumer> consumers = new ArrayList<>();
    private final List<MessageProducer> producers = new ArrayList<>();
    private final List<EmitterInjection> emitters = new ArrayList<>();
    private final List<VertxEventBus> eventbusUsages = new ArrayList<>();
    private final List<CDIEvent> cdiEvents = new ArrayList<>();
    private final List<KafkaClientUsage> kafkaClientUsages = new ArrayList<>();
    private final List<ReactiveReturnType> reactiveReturnTypes = new ArrayList<>();
    private final List<AcknowledgmentHandler> ackHandlers = new ArrayList<>();
    private final List<BatchConsumer> batchConsumers = new ArrayList<>();
    private KafkaConfiguration kafkaConfig;
    private AMQPConfiguration amqpConfig;
    private final List<ConnectorConfiguration> connectors = new ArrayList<>();

    // Messaging indicators for quick filtering
    private static final String[] MESSAGING_INDICATORS = {
            "@Incoming", "@Outgoing", "@Channel",
            "Emitter", "MutinyEmitter",
            "org.eclipse.microprofile.reactive.messaging",
            "io.smallrye.reactive.messaging",
            "EventBus", "vertx",
            "@ConsumeEvent",
            "@Observes", "Event<", ".fire(",
            "KafkaProducer", "KafkaConsumer",
            "AmqpClient", "RabbitMQ",
            "Uni<Message", "Multi<Message",
            ".ack()", ".nack()",
            "List<Message"
    };

    public QuarkusMessagingExtractor(String rootDir) {
        this.rootDir = Paths.get(rootDir);
    }

    /**
     * Extract all messaging metadata from multiple sources
     */
    public QuarkusMessagingMetadata extractAll() {
        System.out.println("🔍 Quarkus Messaging scanning...");

        // Extract from Java source files
        extractFromJavaFiles();

        // Extract from configuration files
        extractFromConfigFiles();

        // Consolidate all findings
        consolidateChannels();

        printExtractionSummary();

        // Build metadata object
        QuarkusMessagingMetadata metadata = new QuarkusMessagingMetadata();
        metadata.setChannels(channels);
        metadata.setConsumers(consumers);
        metadata.setProducers(producers);
        metadata.setEmitters(emitters);
        metadata.setEventbusUsages(eventbusUsages);
        metadata.setCdiEvents(cdiEvents);
        metadata.setConnectors(connectors);
        metadata.setKafkaClientUsages(kafkaClientUsages);
        metadata.setReactiveReturnTypes(reactiveReturnTypes);
        metadata.setAckHandlers(ackHandlers);
        metadata.setBatchConsumers(batchConsumers);
        metadata.setKafkaConfig(kafkaConfig);
        metadata.setAmqpConfig(amqpConfig);

        return metadata;
    }

    /**
     * Extract messaging metadata from Java source files
     */
    private void extractFromJavaFiles() {
        System.out.println("   Scanning Java files...");

        try (Stream<Path> paths = Files.walk(rootDir)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            System.out.println("   Found " + javaFiles.size() + " Java files");

            for (Path javaFile : javaFiles) {
                try {
                    String content = Files.readString(javaFile);
                    String relPath = rootDir.relativize(javaFile).toString();

                    if (hasMessagingContent(content)) {
                        extractFromJavaFile(content, relPath);
                    }
                } catch (Exception e) {
                    System.err.println("   ⚠️  Error processing " + javaFile + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("   ⚠️  Error walking directory tree: " + e.getMessage());
        }
    }

    /**
     * Check if file contains messaging-related code
     */
    private boolean hasMessagingContent(String content) {
        for (String indicator : MESSAGING_INDICATORS) {
            if (content.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract all messaging patterns from a Java file
     */
    private void extractFromJavaFile(String content, String filePath) {
        String packageName = extractPackage(content);
        String className = extractClassName(content);

        // 1. @Incoming consumers
        if (content.contains("@Incoming")) {
            extractIncomingConsumers(content, filePath, packageName, className);
        }

        // 2. @Outgoing producers
        if (content.contains("@Outgoing")) {
            extractOutgoingProducers(content, filePath, packageName, className);
        }

        // 3. Emitter injections
        if (content.contains("Emitter") || content.contains("@Channel")) {
            extractEmitterInjections(content, filePath, className);
            // Also extract producer methods that use emitters
            extractEmitterUsageProducers(content, filePath, packageName, className);
        }

        // 4. Vert.x Event Bus
        if (content.contains("EventBus") || content.contains("@ConsumeEvent")) {
            extractEventBusUsage(content, filePath, className);
        }

        // 5. CDI Events
        if (content.contains("@Observes") || content.contains("Event<") || content.contains(".fire(")) {
            extractCDIEvents(content, filePath, className);
        }

        // 6. Native Kafka clients
        if (content.contains("KafkaProducer") || content.contains("KafkaConsumer")) {
            extractKafkaClientUsages(content, filePath, className);
        }

        // 7. Reactive return types
        if (content.contains("Uni<Message") || content.contains("Multi<Message")) {
            extractReactiveReturnTypes(content, filePath, packageName, className);
        }

        // 8. Acknowledgment handlers
        if (content.contains(".ack()") || content.contains(".nack()")) {
            extractAckHandlers(content, filePath, packageName, className);
        }

        // 9. Batch consumers
        if (content.contains("List<Message")) {
            extractBatchConsumers(content, filePath, packageName, className);
        }
    }

    /**
     * Extract @Incoming message consumers
     */
    private void extractIncomingConsumers(String content, String filePath, String packageName, String className) {
        System.out.println("   Found @Incoming in: " + className);

        String[] lines = content.split("\n");

        // Pattern for @Incoming(channel) and @Incoming(value = "channel")
        Pattern incomingPattern = Pattern.compile("@Incoming\\s*\\(\\s*(?:value\\s*=\\s*)?\"?([\\w\\-.]+)\"?\\s*\\)");

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = incomingPattern.matcher(lines[i]);
            if (matcher.find()) {
                String channelName = matcher.group(1);

                // Find the method signature
                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);
                if (methodInfo != null) {
                    // Extract additional annotations
                    List<String> annotations = extractMethodAnnotations(lines, i);

                    // Determine consumer style
                    String consumerStyle = "reactive";
                    if (annotations.contains("Blocking")) {
                        consumerStyle = "blocking";
                    } else if (!methodInfo.signature.contains("CompletionStage") &&
                            !methodInfo.signature.contains("Uni")) {
                        consumerStyle = "imperative";
                    }

                    // Extract acknowledgment strategy
                    String ackStrategy = null;
                    for (int j = Math.max(0, i - 5); j < i; j++) {
                        Pattern ackPattern = Pattern
                                .compile("@Acknowledgment\\s*\\(\\s*Acknowledgment\\.Strategy\\.(\\w+)\\s*\\)");
                        Matcher ackMatcher = ackPattern.matcher(lines[j]);
                        if (ackMatcher.find()) {
                            ackStrategy = ackMatcher.group(1);
                            break;
                        }
                    }

                    // Check for broadcast/merge
                    boolean broadcast = annotations.contains("Broadcast");
                    boolean merge = annotations.contains("Merge");

                    MessageConsumer consumer = new MessageConsumer();
                    consumer.setClassName(className);
                    consumer.setFilePath(filePath);
                    consumer.setPackageName(packageName);
                    consumer.setMethodName(methodInfo.name);
                    consumer.setChannelName(channelName);
                    consumer.setMessageType(methodInfo.paramType);
                    consumer.setAcknowledgmentStrategy(ackStrategy);
                    consumer.setBroadcast(broadcast);
                    consumer.setMerge(merge);
                    consumer.setAnnotations(annotations);
                    consumer.setMethodSignature(methodInfo.signature);
                    consumer.setReturnType(methodInfo.returnType);
                    consumer.setConsumerStyle(consumerStyle);
                    consumer.setLineNumber(i + 1);

                    consumers.add(consumer);
                }
            }
        }
    }

    /**
     * Extract @Outgoing message producers
     */
    private void extractOutgoingProducers(String content, String filePath, String packageName, String className) {
        System.out.println("   Found @Outgoing in: " + className);

        String[] lines = content.split("\n");

        // Pattern for @Outgoing(channel) and @Outgoing(value = "channel")
        Pattern outgoingPattern = Pattern.compile("@Outgoing\\s*\\(\\s*(?:value\\s*=\\s*)?\"?([\\w\\-.]+)\"?\\s*\\)");

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = outgoingPattern.matcher(lines[i]);
            if (matcher.find()) {
                String channelName = matcher.group(1);

                // Find the method signature
                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);
                if (methodInfo != null) {
                    List<String> annotations = extractMethodAnnotations(lines, i);
                    String snippet = getCodeSnippet(lines, i);

                    MessageProducer producer = new MessageProducer();
                    producer.setClassName(className);
                    producer.setFilePath(filePath);
                    producer.setPackageName(packageName);
                    producer.setMethodName(methodInfo.name);
                    producer.setChannelName(channelName);
                    producer.setMessageType(methodInfo.returnType);
                    producer.setProducerType("method");
                    producer.setAnnotations(annotations);
                    producer.setMethodSignature(methodInfo.signature);
                    producer.setLineNumber(i + 1);
                    producer.setCodeSnippet(snippet);

                    producers.add(producer);
                }
            }
        }
    }

    /**
     * Extract Emitter and @Channel injections (supports multi-line patterns)
     */
    private void extractEmitterInjections(String content, String filePath, String className) {
        System.out.println("   Found Emitter/Channel in: " + className);

        String[] lines = content.split("\n");

        // Single-line pattern for @Channel injection with Emitter
        Pattern singleLinePattern = Pattern.compile(
                "@Channel\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s+(?:@Inject\\s+)?(\\w+Emitter|Channel)\\s*<\\s*([^>]+)\\s*>\\s+(\\w+)");

        // Check for single-line pattern first
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = singleLinePattern.matcher(lines[i]);
            if (matcher.find()) {
                String channelName = matcher.group(1);
                String emitterType = matcher.group(2);
                String messageType = matcher.group(3);
                String variableName = matcher.group(4);

                EmitterInjection emitter = new EmitterInjection();
                emitter.setClassName(className);
                emitter.setFilePath(filePath);
                emitter.setVariableName(variableName);
                emitter.setChannelName(channelName);
                emitter.setMessageType(messageType);
                emitter.setEmitterType(emitterType);
                emitter.setLineNumber(i + 1);

                emitters.add(emitter);
            }
        }

        // Multi-line pattern detection for:
        // @Inject
        // @Channel("channel-name")
        // Emitter<Type> variable;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Look for @Inject or @Channel as starting point
            if (line.contains("@Inject") || line.contains("@Channel")) {
                String channelName = null;

                // Search for @Channel in current and next few lines
                for (int j = i; j < Math.min(i + 5, lines.length); j++) {
                    Pattern channelPattern = Pattern.compile("@Channel\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
                    Matcher channelMatcher = channelPattern.matcher(lines[j]);
                    if (channelMatcher.find()) {
                        channelName = channelMatcher.group(1);
                        break;
                    }
                }

                if (channelName != null) {
                    // Now look for Emitter declaration in the following lines
                    for (int k = i; k < Math.min(i + 5, lines.length); k++) {
                        // Match Emitter<Type> variable or MutinyEmitter<Type> variable
                        Pattern emitterPattern = Pattern.compile(
                                "(Emitter|MutinyEmitter|Channel)\\s*<\\s*([^>]+)\\s*>\\s+(\\w+)\\s*;");
                        Matcher emitterMatcher = emitterPattern.matcher(lines[k]);
                        if (emitterMatcher.find()) {
                            String emitterType = emitterMatcher.group(1);
                            String messageType = emitterMatcher.group(2).trim();
                            String variableName = emitterMatcher.group(3);

                            // Check if we already added this emitter (avoid duplicates)
                            final String finalChannelName = channelName;
                            boolean alreadyExists = emitters.stream()
                                    .anyMatch(e -> e.getVariableName().equals(variableName) &&
                                            e.getChannelName().equals(finalChannelName) &&
                                            e.getClassName().equals(className));

                            if (!alreadyExists) {
                                EmitterInjection emitter = new EmitterInjection();
                                emitter.setClassName(className);
                                emitter.setFilePath(filePath);
                                emitter.setVariableName(variableName);
                                emitter.setChannelName(channelName);
                                emitter.setMessageType(messageType);
                                emitter.setEmitterType(emitterType);
                                emitter.setLineNumber(k + 1);

                                emitters.add(emitter);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract producer methods that use Emitter.send() or sendAndAwait()
     */
    private void extractEmitterUsageProducers(String content, String filePath, String packageName, String className) {
        if (emitters.isEmpty()) {
            return; // No emitters to track
        }

        String[] lines = content.split("\n");

        // Build a map of emitter variable names to their channel names
        Map<String, EmitterInfo> emitterMap = new HashMap<>();
        for (EmitterInjection emitter : emitters) {
            if (emitter.getClassName().equals(className)) {
                emitterMap.put(emitter.getVariableName(),
                        new EmitterInfo(emitter.getChannelName(), emitter.getMessageType()));
            }
        }

        if (emitterMap.isEmpty()) {
            return; // No emitters in this class
        }

        // Look for .send() or .sendAndAwait() calls on emitter variables
        for (int i = 0; i < lines.length; i++) {
            for (Map.Entry<String, EmitterInfo> entry : emitterMap.entrySet()) {
                String varName = entry.getKey();
                EmitterInfo emitterInfo = entry.getValue();

                // Pattern: emitterVariable.send(...) or emitterVariable.sendAndAwait(...)
                Pattern sendPattern = Pattern.compile(varName + "\\s*\\.\\s*(send|sendAndAwait)\\s*\\(");
                Matcher sendMatcher = sendPattern.matcher(lines[i]);

                if (sendMatcher.find()) {
                    String methodName = findEnclosingMethod(lines, i);

                    // Check if we already have this producer (avoid duplicates)
                    final String finalMethodName = methodName;
                    boolean alreadyExists = producers.stream().anyMatch(p -> p.getClassName().equals(className) &&
                            p.getMethodName().equals(finalMethodName) &&
                            p.getChannelName().equals(emitterInfo.channelName));

                    if (!alreadyExists) {
                        // Get method signature
                        MethodInfo methodInfo = findMethodSignatureForLine(lines, i);

                        MessageProducer producer = new MessageProducer();
                        producer.setClassName(className);
                        producer.setFilePath(filePath);
                        producer.setPackageName(packageName);
                        producer.setMethodName(methodName);
                        producer.setChannelName(emitterInfo.channelName);
                        producer.setMessageType(emitterInfo.messageType);
                        producer.setProducerType("emitter");
                        producer.setAnnotations(new ArrayList<>());
                        producer.setMethodSignature(
                                methodInfo != null ? methodInfo.signature : "void " + methodName + "()");
                        producer.setLineNumber(i + 1);
                        producer.setCodeSnippet(getCodeSnippet(lines, i, 3));

                        producers.add(producer);
                    }
                }
            }
        }
    }

    /**
     * Extract Vert.x Event Bus usage
     */
    private void extractEventBusUsage(String content, String filePath, String className) {
        System.out.println("   Found Event Bus usage in: " + className);

        String[] lines = content.split("\n");

        // Pattern for @ConsumeEvent
        Pattern consumePattern = Pattern.compile("@ConsumeEvent\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = consumePattern.matcher(lines[i]);
            if (matcher.find()) {
                String address = matcher.group(1);
                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);

                if (methodInfo != null) {
                    VertxEventBus eventbus = new VertxEventBus();
                    eventbus.setClassName(className);
                    eventbus.setFilePath(filePath);
                    eventbus.setAddress(address);
                    eventbus.setOperation("consumer");
                    eventbus.setMessageType(methodInfo.paramType);
                    eventbus.setMethodName(methodInfo.name);
                    eventbus.setLineNumber(i + 1);

                    eventbusUsages.add(eventbus);
                }
            }
        }

        // Pattern for eventBus.send/publish
        Pattern sendPattern = Pattern.compile("eventBus\\.(send|publish|request)\\s*\\(\\s*\"([^\"]+)\"");

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = sendPattern.matcher(lines[i]);
            if (matcher.find()) {
                String operation = matcher.group(1);
                String address = matcher.group(2);
                String methodName = findEnclosingMethod(lines, i);

                VertxEventBus eventbus = new VertxEventBus();
                eventbus.setClassName(className);
                eventbus.setFilePath(filePath);
                eventbus.setAddress(address);
                eventbus.setOperation(operation);
                eventbus.setMessageType("Unknown");
                eventbus.setMethodName(methodName);
                eventbus.setLineNumber(i + 1);

                eventbusUsages.add(eventbus);
            }
        }
    }

    /**
     * Extract native Kafka client usages (KafkaProducer, KafkaConsumer)
     */
    private void extractKafkaClientUsages(String content, String filePath, String className) {
        // Pattern for KafkaProducer/KafkaConsumer declarations
        Pattern clientPattern = Pattern.compile(
                "(KafkaProducer|KafkaConsumer)<([^>]+)>\\s+(\\w+)\\s*=",
                Pattern.MULTILINE);

        Matcher matcher = clientPattern.matcher(content);
        while (matcher.find()) {
            String clientType = matcher.group(1);
            String genericType = matcher.group(2);
            String variableName = matcher.group(3);

            // Check if already exists
            boolean exists = kafkaClientUsages.stream()
                    .anyMatch(k -> k.getClassName().equals(className) &&
                            k.getVariableName().equals(variableName));

            if (!exists) {
                KafkaClientUsage usage = new KafkaClientUsage();
                usage.setClassName(className);
                usage.setFilePath(filePath);
                usage.setClientType(clientType);
                usage.setGenericType(genericType);
                usage.setVariableName(variableName);

                kafkaClientUsages.add(usage);
                System.out.println("  Found Kafka client: " + clientType + "<" + genericType + "> " + variableName);
            }
        }
    }

    /**
     * Extract reactive return types (Uni<Message<T>>, Multi<Message<T>>)
     */
    private void extractReactiveReturnTypes(String content, String filePath, String packageName, String className) {
        // Pattern for methods returning Uni<Message<T>> or Multi<Message<T>>
        Pattern reactivePattern = Pattern.compile(
                "@(Incoming|Outgoing)\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*" +
                        "(?:public\\s+)?(Uni|Multi)<Message<([^>]+)>>\\s+(\\w+)\\s*\\(",
                Pattern.MULTILINE | Pattern.DOTALL);

        Matcher matcher = reactivePattern.matcher(content);
        while (matcher.find()) {
            String annotation = matcher.group(1);
            String channel = matcher.group(2);
            String reactiveType = matcher.group(3);
            String messageType = matcher.group(4);
            String methodName = matcher.group(5);

            // Check if already exists
            boolean exists = reactiveReturnTypes.stream()
                    .anyMatch(r -> r.getClassName().equals(className) &&
                            r.getMethodName().equals(methodName));

            if (!exists) {
                ReactiveReturnType returnType = new ReactiveReturnType();
                returnType.setClassName(className);
                returnType.setFilePath(filePath);
                returnType.setMethodName(methodName);
                returnType.setReactiveType(reactiveType);
                returnType.setMessageType(messageType);
                returnType.setChannel(channel);
                returnType.setAnnotation(annotation);

                reactiveReturnTypes.add(returnType);
                System.out.println(
                        "  Found reactive return: " + reactiveType + "<Message<" + messageType + ">> in " + methodName);
            }
        }
    }

    /**
     * Extract acknowledgment handlers (Message.ack(), Message.nack())
     */
    private void extractAckHandlers(String content, String filePath, String packageName, String className) {
        // Pattern for methods with Message parameter and ack/nack calls
        Pattern methodPattern = Pattern.compile(
                "@Incoming\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*" +
                        "(?:public\\s+)?(?:\\w+)\\s+(\\w+)\\s*\\(\\s*Message<([^>]+)>\\s+(\\w+)\\s*\\)",
                Pattern.MULTILINE | Pattern.DOTALL);

        Matcher matcher = methodPattern.matcher(content);
        while (matcher.find()) {
            String channel = matcher.group(1);
            String methodName = matcher.group(2);
            String messageType = matcher.group(3);
            String paramName = matcher.group(4);

            // Check for ack/nack calls
            boolean hasAck = content.contains(paramName + ".ack()");
            boolean hasNack = content.contains(paramName + ".nack(");

            if (hasAck || hasNack) {
                // Check if already exists
                boolean exists = ackHandlers.stream()
                        .anyMatch(a -> a.getClassName().equals(className) &&
                                a.getMethodName().equals(methodName));

                if (!exists) {
                    AcknowledgmentHandler handler = new AcknowledgmentHandler();
                    handler.setClassName(className);
                    handler.setFilePath(filePath);
                    handler.setMethodName(methodName);
                    handler.setChannel(channel);
                    handler.setMessageType(messageType);
                    handler.setHasAck(hasAck);
                    handler.setHasNack(hasNack);

                    ackHandlers.add(handler);
                    System.out.println(
                            "  Found ack handler in " + methodName + " (ack=" + hasAck + ", nack=" + hasNack + ")");
                }
            }
        }
    }

    /**
     * Extract batch consumers (methods with List<Message<T>> parameter)
     */
    private void extractBatchConsumers(String content, String filePath, String packageName, String className) {
        // Pattern for @Incoming methods with List<Message<T>> parameter
        Pattern batchPattern = Pattern.compile(
                "@Incoming\\s*\\(\\s*\"([^\"]+)\"\\s*\\)\\s*" +
                        "(?:public\\s+)?(?:\\w+)\\s+(\\w+)\\s*\\(\\s*List<Message<([^>]+)>>\\s+(\\w+)\\s*\\)",
                Pattern.MULTILINE | Pattern.DOTALL);

        Matcher matcher = batchPattern.matcher(content);
        while (matcher.find()) {
            String channel = matcher.group(1);
            String methodName = matcher.group(2);
            String messageType = matcher.group(3);
            String paramName = matcher.group(4);

            // Check if already exists
            boolean exists = batchConsumers.stream()
                    .anyMatch(b -> b.getClassName().equals(className) &&
                            b.getMethodName().equals(methodName));

            if (!exists) {
                BatchConsumer consumer = new BatchConsumer();
                consumer.setClassName(className);
                consumer.setFilePath(filePath);
                consumer.setMethodName(methodName);
                consumer.setChannel(channel);
                consumer.setMessageType(messageType);
                consumer.setParameterName(paramName);

                batchConsumers.add(consumer);
                System.out
                        .println("  Found batch consumer: " + methodName + " with List<Message<" + messageType + ">>");
            }
        }
    }

    /**
     * Extract CDI Events
     */
    private void extractCDIEvents(String content, String filePath, String className) {
        System.out.println("   Found CDI Events in: " + className);

        String[] lines = content.split("\n");

        // Pattern for @Observes
        Pattern observesPattern = Pattern.compile("@Observes\\s+(\\w+(?:<[^>]+>)?)");

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = observesPattern.matcher(lines[i]);
            if (matcher.find()) {
                String eventType = matcher.group(1);
                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);

                CDIEvent cdiEvent = new CDIEvent();
                cdiEvent.setClassName(className);
                cdiEvent.setFilePath(filePath);
                cdiEvent.setEventType(eventType);
                cdiEvent.setOperation("observe");
                cdiEvent.setMethodName(methodInfo != null ? methodInfo.name : "Unknown");
                cdiEvent.setLineNumber(i + 1);

                cdiEvents.add(cdiEvent);
            }
        }

        // Pattern for Event.fire()
        Pattern firePattern = Pattern.compile("(\\w+)\\.fire\\s*\\(");

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = firePattern.matcher(lines[i]);
            if (matcher.find()) {
                String methodName = findEnclosingMethod(lines, i);

                // Try to infer event type from context
                String eventType = "Unknown";
                for (int j = Math.max(0, i - 10); j < i; j++) {
                    Pattern typePattern = Pattern.compile("Event<(\\w+)>");
                    Matcher typeMatcher = typePattern.matcher(lines[j]);
                    if (typeMatcher.find()) {
                        eventType = typeMatcher.group(1);
                        break;
                    }
                }

                CDIEvent cdiEvent = new CDIEvent();
                cdiEvent.setClassName(className);
                cdiEvent.setFilePath(filePath);
                cdiEvent.setEventType(eventType);
                cdiEvent.setOperation("fire");
                cdiEvent.setMethodName(methodName);
                cdiEvent.setLineNumber(i + 1);

                cdiEvents.add(cdiEvent);
            }
        }
    }

    /**
     * Extract messaging configuration from application.properties/yml
     */
    private void extractFromConfigFiles() {
        System.out.println("   Scanning configuration files...");

        try (Stream<Path> paths = Files.walk(rootDir)) {
            // Look for application.properties
            List<Path> propsFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("application.*\\.properties"))
                    .toList();

            for (Path propsFile : propsFiles) {
                try {
                    parsePropertiesFile(propsFile);
                } catch (Exception e) {
                    System.err.println("   ⚠️  Error parsing " + propsFile + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("   ⚠️  Error finding config files: " + e.getMessage());
        }
    }

    /**
     * Parse application.properties for messaging config
     */
    private void parsePropertiesFile(Path propsFile) throws Exception {
        System.out.println("   Parsing: " + propsFile.getFileName());

        String relPath = rootDir.relativize(propsFile).toString();
        Map<String, Map<String, String>> channelConfigs = new HashMap<>();

        KafkaConfiguration kafkaConfig = new KafkaConfiguration();
        AMQPConfiguration amqpConfig = new AMQPConfiguration();

        try (BufferedReader reader = new BufferedReader(new FileReader(propsFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }

                String key = parts[0].trim();
                String value = parts[1].trim();

                // Extract messaging channel configuration
                if (line.startsWith("mp.messaging.")) {
                    // Extract channel name from key
                    // Format: mp.messaging.incoming.channel-name.property or
                    // mp.messaging.outgoing.channel-name.property
                    Pattern pattern = Pattern.compile("mp\\.messaging\\.(incoming|outgoing)\\.([^.]+)\\.(.+)");
                    Matcher matcher = pattern.matcher(key);
                    if (matcher.find()) {
                        String direction = matcher.group(1);
                        String channelName = matcher.group(2);
                        String property = matcher.group(3);

                        String mapKey = direction + ":" + channelName;
                        channelConfigs.computeIfAbsent(mapKey, k -> new HashMap<>());
                        channelConfigs.get(mapKey).put(property, value);
                    }
                }

                // Extract Kafka-specific configuration
                if (key.startsWith("kafka.") || key.contains(".kafka.")) {
                    extractKafkaConfig(key, value, kafkaConfig);
                }

                // Extract AMQP-specific configuration
                if (key.startsWith("amqp-") || key.contains(".amqp.")) {
                    extractAMQPConfig(key, value, amqpConfig);
                }
            }
        }

        // Create connector configurations
        for (Map.Entry<String, Map<String, String>> entry : channelConfigs.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String channelName = parts[1];
            Map<String, String> props = entry.getValue();

            String connector = props.getOrDefault("connector", "unknown");

            ConnectorConfiguration config = new ConnectorConfiguration();
            config.setConnectorName(connector);
            config.setConnectorType(connector);
            config.setChannels(Arrays.asList(channelName));
            config.setProperties(props);
            config.setSource(relPath);

            connectors.add(config);
        }

        // Store Kafka configuration if it has any properties
        if (!kafkaConfig.getBootstrapServers().isEmpty() ||
                !kafkaConfig.getGroupId().isEmpty() ||
                !kafkaConfig.getAdditionalProperties().isEmpty()) {
            this.kafkaConfig = kafkaConfig;
        }

        // Store AMQP configuration if it has any properties
        if ((amqpConfig.getHost() != null && !amqpConfig.getHost().isEmpty()) ||
                amqpConfig.getPort() != null ||
                !amqpConfig.getAdditionalProperties().isEmpty()) {
            this.amqpConfig = amqpConfig;
        }
    }

    /**
     * Extract Kafka-specific configuration
     */
    private void extractKafkaConfig(String key, String value, KafkaConfiguration config) {
        if (key.equals("kafka.bootstrap.servers") || key.contains("bootstrap.servers")) {
            config.setBootstrapServers(value);
        } else if (key.contains(".group.id") || key.equals("kafka.group.id")) {
            config.setGroupId(value);
        } else if (key.contains(".key.serializer")) {
            config.setKeySerializer(value);
        } else if (key.contains(".value.serializer")) {
            config.setValueSerializer(value);
        } else if (key.contains(".key.deserializer")) {
            config.setKeyDeserializer(value);
        } else if (key.contains(".value.deserializer")) {
            config.setValueDeserializer(value);
        } else if (key.contains(".auto.offset.reset")) {
            config.setAutoOffsetReset(value);
        } else if (key.contains(".enable.auto.commit")) {
            config.setEnableAutoCommit(value);
        } else {
            // Store other Kafka properties
            config.getAdditionalProperties().put(key, value);
        }
    }

    /**
     * Extract AMQP-specific configuration
     */
    private void extractAMQPConfig(String key, String value, AMQPConfiguration config) {
        if (key.contains(".host") || key.equals("amqp-host")) {
            config.setHost(value);
        } else if (key.contains(".port") || key.equals("amqp-port")) {
            try {
                config.setPort(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port value: " + value);
            }
        } else if (key.contains(".username") || key.equals("amqp-username")) {
            config.setUsername(value);
        } else if (key.contains(".password") || key.equals("amqp-password")) {
            config.setPassword(value);
        } else if (key.contains(".virtual-host") || key.equals("amqp-virtual-host")) {
            config.setVirtualHost(value);
        } else if (key.contains(".durable")) {
            config.setDurable(value);
        } else if (key.contains(".auto-acknowledge")) {
            config.setAutoAcknowledge(value);
        } else {
            // Store other AMQP properties
            config.getAdditionalProperties().put(key, value);
        }
    }

    // Helper methods

    private String extractPackage(String content) {
        Pattern pattern = Pattern.compile("package\\s+([\\w.]+);");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractClassName(String content) {
        Pattern pattern = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "Unknown";
    }

    private MethodInfo findMethodAfterAnnotation(String[] lines, int annotationLine) {
        for (int i = annotationLine; i < Math.min(lines.length, annotationLine + 10); i++) {
            Pattern pattern = Pattern
                    .compile("(?:public|private|protected)?\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)");
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                String returnType = matcher.group(1);
                String methodName = matcher.group(2);
                String params = matcher.group(3);

                // Infer message type from parameters
                String paramType = "Unknown";
                if (params != null && !params.trim().isEmpty()) {
                    String[] paramParts = params.split(",");
                    if (paramParts.length > 0) {
                        String firstParam = paramParts[0].trim();
                        String[] typeParts = firstParam.split("\\s+");
                        if (typeParts.length > 0) {
                            paramType = typeParts[0];
                        }
                    }
                }

                String signature = returnType + " " + methodName + "(" + params + ")";
                return new MethodInfo(methodName, signature, returnType, paramType);
            }
        }

        return null;
    }

    private MethodInfo findMethodSignatureForLine(String[] lines, int lineNum) {
        for (int i = lineNum - 1; i >= Math.max(0, lineNum - 100); i--) {
            Pattern pattern = Pattern
                    .compile("(?:public|private|protected)?\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)");
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                String returnType = matcher.group(1);
                String methodName = matcher.group(2);

                return new MethodInfo(methodName, lines[i].trim(), returnType, "Unknown");
            }
        }
        return null;
    }

    private String findEnclosingMethod(String[] lines, int lineNum) {
        for (int i = lineNum - 1; i >= Math.max(0, lineNum - 50); i--) {
            Pattern pattern = Pattern.compile("(?:public|private|protected)?\\s+\\w+\\s+(\\w+)\\s*\\([^)]*\\)");
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "Unknown";
    }

    private List<String> extractMethodAnnotations(String[] lines, int startLine) {
        List<String> annotations = new ArrayList<>();
        for (int i = Math.max(0, startLine - 10); i <= startLine; i++) {
            if (lines[i].contains("@")) {
                Pattern pattern = Pattern.compile("@(\\w+)");
                Matcher matcher = pattern.matcher(lines[i]);
                while (matcher.find()) {
                    annotations.add(matcher.group(1));
                }
            }
        }
        return annotations;
    }

    private String getCodeSnippet(String[] lines, int lineNum) {
        return getCodeSnippet(lines, lineNum, 2);
    }

    private String getCodeSnippet(String[] lines, int lineNum, int context) {
        int start = Math.max(0, lineNum - context);
        int end = Math.min(lines.length, lineNum + context + 1);
        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            snippet.append(lines[i]).append("\n");
        }
        return snippet.toString().trim();
    }

    /**
     * Consolidate channel information from all sources
     */
    private void consolidateChannels() {
        Map<String, ChannelData> channelMap = new HashMap<>();

        // From consumers
        for (MessageConsumer consumer : consumers) {
            String key = "incoming:" + consumer.getChannelName();
            if (!channelMap.containsKey(key)) {
                ChannelData data = new ChannelData();
                data.name = consumer.getChannelName();
                data.type = "incoming";
                data.connector = "unknown";
                data.files = new HashSet<>();
                data.config = new HashMap<>();
                data.source = "annotation";
                channelMap.put(key, data);
            }
            channelMap.get(key).files.add(consumer.getFilePath());
        }

        // From producers
        for (MessageProducer producer : producers) {
            String key = "outgoing:" + producer.getChannelName();
            if (!channelMap.containsKey(key)) {
                ChannelData data = new ChannelData();
                data.name = producer.getChannelName();
                data.type = "outgoing";
                data.connector = "unknown";
                data.files = new HashSet<>();
                data.config = new HashMap<>();
                data.source = "annotation";
                channelMap.put(key, data);
            }
            channelMap.get(key).files.add(producer.getFilePath());
        }

        // Merge with connector configurations
        for (ConnectorConfiguration connector : connectors) {
            for (String channelName : connector.getChannels()) {
                for (String type : Arrays.asList("incoming", "outgoing")) {
                    String key = type + ":" + channelName;
                    if (channelMap.containsKey(key)) {
                        channelMap.get(key).connector = connector.getConnectorType();
                        channelMap.get(key).config.putAll(connector.getProperties());
                    }
                }
            }
        }

        // Convert to MessagingChannel objects
        for (ChannelData data : channelMap.values()) {
            MessagingChannel channel = new MessagingChannel();
            channel.setName(data.name);
            channel.setType(data.type);
            channel.setConnector(data.connector);
            channel.setTopicOrQueue(data.config.getOrDefault("topic", data.config.getOrDefault("queue", null)));
            channel.setUsedInFiles(new ArrayList<>(data.files));
            channel.setConfiguration(data.config);
            channel.setDefinitionSource(data.source);
            channels.add(channel);
        }
    }

    private void printExtractionSummary() {
        System.out.println("\n✅ Quarkus Messaging extraction complete:");
        System.out.println("   - " + channels.size() + " Channels");
        System.out.println("   - " + consumers.size() + " Message Consumers");
        System.out.println("   - " + producers.size() + " Message Producers");
        System.out.println("   - " + emitters.size() + " Emitter Injections");
        System.out.println("   - " + eventbusUsages.size() + " Event Bus Usages");
        System.out.println("   - " + cdiEvents.size() + " CDI Events");
        System.out.println("   - " + kafkaClientUsages.size() + " Native Kafka Clients");
        System.out.println("   - " + reactiveReturnTypes.size() + " Reactive Return Types");
        System.out.println("   - " + ackHandlers.size() + " Acknowledgment Handlers");
        System.out.println("   - " + batchConsumers.size() + " Batch Consumers");
        System.out.println("   - " + connectors.size() + " Connector Configurations");
    }

    // Helper classes

    private static class MethodInfo {
        String name;
        String signature;
        String returnType;
        String paramType;

        MethodInfo(String name, String signature, String returnType, String paramType) {
            this.name = name;
            this.signature = signature;
            this.returnType = returnType;
            this.paramType = paramType;
        }
    }

    private static class EmitterInfo {
        String channelName;
        String messageType;

        EmitterInfo(String channelName, String messageType) {
            this.channelName = channelName;
            this.messageType = messageType;
        }
    }

    private static class ChannelData {
        String name;
        String type;
        String connector;
        Set<String> files;
        Map<String, String> config;
        String source;
    }
}

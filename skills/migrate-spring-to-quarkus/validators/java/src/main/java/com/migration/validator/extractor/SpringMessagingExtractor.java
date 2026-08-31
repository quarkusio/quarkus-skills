package com.migration.validator.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.model.SpringMessagingModels.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts messaging metadata from Spring applications.
 * 
 * Detects messaging through:
 * - @KafkaListener, @RabbitListener, @JmsListener
 * - KafkaTemplate, RabbitTemplate, JmsTemplate
 * - @StreamListener, @EnableBinding (Spring Cloud Stream)
 * - application.properties/yml configuration
 * 
 */
public class SpringMessagingExtractor {

    private final Path rootDir;
    private final List<SpringDestination> destinations = new ArrayList<>();
    private final List<SpringMessageListener> listeners = new ArrayList<>();
    private final List<SpringMessageProducer> producers = new ArrayList<>();
    private final List<SpringMessagingConfig> configs = new ArrayList<>();
    private final List<SpringReplyingTemplate> replyingTemplates = new ArrayList<>();
    private final List<SpringSendTo> sendToAnnotations = new ArrayList<>();
    private final List<SpringContainerFactory> containerFactories = new ArrayList<>();

    // Messaging indicators for quick filtering
    private static final String[] MESSAGING_INDICATORS = {
            "@KafkaListener", "@RabbitListener", "@JmsListener",
            "KafkaTemplate", "RabbitTemplate", "JmsTemplate",
            "@StreamListener", "@EnableBinding",
            "org.springframework.kafka",
            "org.springframework.amqp",
            "org.springframework.jms",
            "org.springframework.cloud.stream"
    };

    public SpringMessagingExtractor(String rootDir) {
        this.rootDir = Paths.get(rootDir);
    }

    /**
     * Extract all Spring messaging metadata
     */
    public SpringMessagingMetadata extractAll() {
        System.out.println("🔍 Spring Messaging scanning...");

        // Extract from Java source files
        extractFromJavaFiles();

        // Extract from configuration files
        extractFromConfigFiles();

        // Consolidate destinations
        consolidateDestinations();

        printExtractionSummary();

        // Build metadata object
        SpringMessagingMetadata metadata = new SpringMessagingMetadata();
        metadata.setDestinations(destinations);
        metadata.setListeners(listeners);
        metadata.setProducers(producers);
        metadata.setConfigs(configs);
        metadata.setReplyingTemplates(replyingTemplates);
        metadata.setSendToAnnotations(sendToAnnotations);
        metadata.setContainerFactories(containerFactories);

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
     * Check if file contains Spring messaging code
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
        // Extract package and class name
        String packageName = extractPackage(content);
        String className = extractClassName(content);

        // Extract Kafka listeners
        if (content.contains("@KafkaListener")) {
            extractKafkaListeners(content, filePath, packageName, className);
        }

        // Extract RabbitMQ listeners
        if (content.contains("@RabbitListener")) {
            extractRabbitListeners(content, filePath, packageName, className);
        }

        // Extract JMS listeners
        if (content.contains("@JmsListener")) {
            extractJmsListeners(content, filePath, packageName, className);
        }

        // Extract Stream listeners
        if (content.contains("@StreamListener")) {
            extractStreamListeners(content, filePath, packageName, className);
        }

        // Extract Kafka producers
        if (content.contains("KafkaTemplate")) {
            extractKafkaProducers(content, filePath, packageName, className);
        }

        // Extract RabbitMQ producers
        if (content.contains("rabbitTemplate") || content.contains("RabbitTemplate")) {
            extractRabbitProducers(content, filePath, packageName, className);
        }

        // Extract JMS producers
        if (content.contains("jmsTemplate") || content.contains("JmsTemplate")) {
            extractJmsProducers(content, filePath, packageName, className);
        }

        // Extract @SendTo annotations
        if (content.contains("@SendTo")) {
            extractSendToAnnotations(content, filePath, packageName, className);
        }

        // Extract ReplyingKafkaTemplate usage
        if (content.contains("ReplyingKafkaTemplate")) {
            extractReplyingTemplates(content, filePath, packageName, className);
        }

        // Extract container factory configurations
        if (content.contains("ContainerFactory")) {
            extractContainerFactories(content, filePath, packageName, className);
        }
    }

    /**
     * Extract @KafkaListener annotations
     */
    private void extractKafkaListeners(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("@KafkaListener")) {
                String annotationBlock = getAnnotationBlock(lines, i);

                String topics = extractAnnotationParam(annotationBlock, "topics");
                String topicPattern = extractAnnotationParam(annotationBlock, "topicPattern");
                String groupId = extractAnnotationParam(annotationBlock, "groupId");
                String concurrency = extractAnnotationParam(annotationBlock, "concurrency");
                String containerFactory = extractAnnotationParam(annotationBlock, "containerFactory");

                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);
                String destination = topics != null ? topics : (topicPattern != null ? topicPattern : "Unknown");

                SpringMessageListener listener = new SpringMessageListener();
                listener.setClassName(className);
                listener.setFilePath(filePath);
                listener.setPackageName(packageName);
                listener.setMethodName(methodInfo.name);
                listener.setListenerType("kafka");
                listener.setDestinationName(destination);
                listener.setGroupId(groupId);
                listener.setConcurrency(concurrency);
                listener.setAnnotations(Arrays.asList("@KafkaListener"));
                listener.setMethodSignature(methodInfo.signature);
                listener.setMessageType(methodInfo.messageType);
                listener.setLineNumber(i + 1);
                listener.setContainerFactory(containerFactory);

                listeners.add(listener);
            }
        }
    }

    /**
     * Extract @RabbitListener annotations
     */
    private void extractRabbitListeners(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("@RabbitListener")) {
                String annotationBlock = getAnnotationBlock(lines, i);

                String queues = extractAnnotationParam(annotationBlock, "queues");
                String queuesToDeclare = extractAnnotationParam(annotationBlock, "queuesToDeclare");
                String concurrency = extractAnnotationParam(annotationBlock, "concurrency");

                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);
                String destination = queues != null ? queues : (queuesToDeclare != null ? queuesToDeclare : "Unknown");

                SpringMessageListener listener = new SpringMessageListener();
                listener.setClassName(className);
                listener.setFilePath(filePath);
                listener.setPackageName(packageName);
                listener.setMethodName(methodInfo.name);
                listener.setListenerType("rabbitmq");
                listener.setDestinationName(destination);
                listener.setGroupId(null);
                listener.setConcurrency(concurrency);
                listener.setAnnotations(Arrays.asList("@RabbitListener"));
                listener.setMethodSignature(methodInfo.signature);
                listener.setMessageType(methodInfo.messageType);
                listener.setLineNumber(i + 1);
                listener.setContainerFactory(null);

                listeners.add(listener);
            }
        }
    }

    /**
     * Extract @JmsListener annotations
     */
    private void extractJmsListeners(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("@JmsListener")) {
                String annotationBlock = getAnnotationBlock(lines, i);

                String destination = extractAnnotationParam(annotationBlock, "destination");
                String concurrency = extractAnnotationParam(annotationBlock, "concurrency");
                String containerFactory = extractAnnotationParam(annotationBlock, "containerFactory");

                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);

                SpringMessageListener listener = new SpringMessageListener();
                listener.setClassName(className);
                listener.setFilePath(filePath);
                listener.setPackageName(packageName);
                listener.setMethodName(methodInfo.name);
                listener.setListenerType("jms");
                listener.setDestinationName(destination != null ? destination : "Unknown");
                listener.setGroupId(null);
                listener.setConcurrency(concurrency);
                listener.setAnnotations(Arrays.asList("@JmsListener"));
                listener.setMethodSignature(methodInfo.signature);
                listener.setMessageType(methodInfo.messageType);
                listener.setLineNumber(i + 1);
                listener.setContainerFactory(containerFactory);

                listeners.add(listener);
            }
        }
    }

    /**
     * Extract @StreamListener annotations (Spring Cloud Stream)
     */
    private void extractStreamListeners(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("@StreamListener")) {
                String annotationBlock = getAnnotationBlock(lines, i);

                // Extract channel name
                Pattern pattern = Pattern.compile("@StreamListener\\s*\\(\\s*[\"']?([^\"')\\s]+)");
                Matcher matcher = pattern.matcher(annotationBlock);
                String channel = matcher.find() ? matcher.group(1) : "Unknown";

                MethodInfo methodInfo = findMethodAfterAnnotation(lines, i);

                SpringMessageListener listener = new SpringMessageListener();
                listener.setClassName(className);
                listener.setFilePath(filePath);
                listener.setPackageName(packageName);
                listener.setMethodName(methodInfo.name);
                listener.setListenerType("stream");
                listener.setDestinationName(channel);
                listener.setGroupId(null);
                listener.setConcurrency(null);
                listener.setAnnotations(Arrays.asList("@StreamListener"));
                listener.setMethodSignature(methodInfo.signature);
                listener.setMessageType(methodInfo.messageType);
                listener.setLineNumber(i + 1);
                listener.setContainerFactory(null);

                listeners.add(listener);
            }
        }
    }

    /**
     * Extract KafkaTemplate usage
     */
    private void extractKafkaProducers(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(".send(") && lines[i].toLowerCase().contains("kafka")) {
                String methodName = findEnclosingMethod(lines, i);

                // Extract topic
                Pattern topicPattern = Pattern.compile("\\.send\\s*\\(\\s*[\"']([^\"']+)[\"']");
                Matcher topicMatcher = topicPattern.matcher(lines[i]);
                String topic = topicMatcher.find() ? topicMatcher.group(1) : "Unknown";

                // Find template variable
                Pattern templatePattern = Pattern.compile("(\\w+)\\.send\\s*\\(");
                Matcher templateMatcher = templatePattern.matcher(lines[i]);
                String templateVar = templateMatcher.find() ? templateMatcher.group(1) : "kafkaTemplate";

                String msgType = inferMessageType(lines, i);
                String snippet = getCodeSnippet(lines, i);

                SpringMessageProducer producer = new SpringMessageProducer();
                producer.setClassName(className);
                producer.setFilePath(filePath);
                producer.setPackageName(packageName);
                producer.setMethodName(methodName);
                producer.setProducerType("kafka");
                producer.setDestinationVariable(topic);
                producer.setMessageType(msgType);
                producer.setLineNumber(i + 1);
                producer.setTemplateVariable(templateVar);
                producer.setCodeSnippet(snippet);

                producers.add(producer);
            }
        }
    }

    /**
     * Extract RabbitTemplate usage
     */
    private void extractRabbitProducers(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("rabbitTemplate") &&
                    (lines[i].contains(".convertAndSend(") || lines[i].contains(".send("))) {
                String methodName = findEnclosingMethod(lines, i);

                // Extract exchange/routing key
                Pattern sendPattern = Pattern.compile("\\.(?:convertAndSend|send)\\s*\\(\\s*[\"']([^\"']+)[\"']");
                Matcher sendMatcher = sendPattern.matcher(lines[i]);
                String destination = sendMatcher.find() ? sendMatcher.group(1) : "Unknown";

                Pattern templatePattern = Pattern.compile("(\\w+)\\.(?:convertAndSend|send)");
                Matcher templateMatcher = templatePattern.matcher(lines[i]);
                String templateVar = templateMatcher.find() ? templateMatcher.group(1) : "rabbitTemplate";

                String msgType = inferMessageType(lines, i);
                String snippet = getCodeSnippet(lines, i);

                SpringMessageProducer producer = new SpringMessageProducer();
                producer.setClassName(className);
                producer.setFilePath(filePath);
                producer.setPackageName(packageName);
                producer.setMethodName(methodName);
                producer.setProducerType("rabbitmq");
                producer.setDestinationVariable(destination);
                producer.setMessageType(msgType);
                producer.setLineNumber(i + 1);
                producer.setTemplateVariable(templateVar);
                producer.setCodeSnippet(snippet);

                producers.add(producer);
            }
        }
    }

    /**
     * Extract JmsTemplate usage
     */
    private void extractJmsProducers(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("jmsTemplate") &&
                    (lines[i].contains(".convertAndSend(") || lines[i].contains(".send("))) {
                String methodName = findEnclosingMethod(lines, i);

                // Extract destination
                Pattern sendPattern = Pattern.compile("\\.(?:convertAndSend|send)\\s*\\(\\s*[\"']([^\"']+)[\"']");
                Matcher sendMatcher = sendPattern.matcher(lines[i]);
                String destination = sendMatcher.find() ? sendMatcher.group(1) : "Unknown";

                Pattern templatePattern = Pattern.compile("(\\w+)\\.(?:convertAndSend|send)");
                Matcher templateMatcher = templatePattern.matcher(lines[i]);
                String templateVar = templateMatcher.find() ? templateMatcher.group(1) : "jmsTemplate";

                String msgType = inferMessageType(lines, i);
                String snippet = getCodeSnippet(lines, i);

                SpringMessageProducer producer = new SpringMessageProducer();
                producer.setClassName(className);
                producer.setFilePath(filePath);
                producer.setPackageName(packageName);
                producer.setMethodName(methodName);
                producer.setProducerType("jms");
                producer.setDestinationVariable(destination);
                producer.setMessageType(msgType);
                producer.setLineNumber(i + 1);
                producer.setTemplateVariable(templateVar);
                producer.setCodeSnippet(snippet);

                producers.add(producer);
            }
        }
    }

    /**
     * Extract @SendTo annotations for request-reply patterns
     */
    private void extractSendToAnnotations(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("@SendTo")) {
                // Extract destination
                Pattern sendToPattern = Pattern.compile("@SendTo\\s*\\(\\s*[\"']([^\"']+)[\"']");
                Matcher sendToMatcher = sendToPattern.matcher(lines[i]);
                String sendToDest = sendToMatcher.find() ? sendToMatcher.group(1) : "Unknown";

                // Find associated listener
                String listenerDest = "Unknown";
                for (int j = Math.max(0, i - 10); j < i; j++) {
                    if (lines[j].contains("@KafkaListener") || lines[j].contains("@RabbitListener") ||
                            lines[j].contains("@JmsListener")) {
                        Pattern destPattern = Pattern
                                .compile("(?:topics?|queues?|destination)\\s*=\\s*[\"']([^\"']+)[\"']");
                        Matcher destMatcher = destPattern.matcher(lines[j]);
                        if (destMatcher.find()) {
                            listenerDest = destMatcher.group(1);
                            break;
                        }
                    }
                }

                // Find method name
                String methodName = "Unknown";
                for (int j = i; j < Math.min(lines.length, i + 5); j++) {
                    Pattern methodPattern = Pattern.compile("(?:public|private|protected)?\\s+\\w+\\s+(\\w+)\\s*\\(");
                    Matcher methodMatcher = methodPattern.matcher(lines[j]);
                    if (methodMatcher.find()) {
                        methodName = methodMatcher.group(1);
                        break;
                    }
                }

                SpringSendTo sendTo = new SpringSendTo();
                sendTo.setClassName(className);
                sendTo.setFilePath(filePath);
                sendTo.setMethodName(methodName);
                sendTo.setSendToDestination(sendToDest);
                sendTo.setListenerDestination(listenerDest);
                sendTo.setLineNumber(i + 1);

                sendToAnnotations.add(sendTo);
            }
        }
    }

    /**
     * Extract ReplyingKafkaTemplate usage for synchronous request-reply
     */
    private void extractReplyingTemplates(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        // Find ReplyingKafkaTemplate declarations
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("ReplyingKafkaTemplate") &&
                    (lines[i].contains("=") || lines[i].contains("new"))) {
                // Extract variable name
                Pattern varPattern = Pattern.compile("(\\w+)\\s*=.*ReplyingKafkaTemplate");
                Matcher varMatcher = varPattern.matcher(lines[i]);
                String variableName = varMatcher.find() ? varMatcher.group(1) : "replyingTemplate";

                // Look for sendAndReceive calls
                String requestTopic = "Unknown";
                String replyTopic = null;

                for (int j = i; j < Math.min(lines.length, i + 50); j++) {
                    if (lines[j].contains(variableName + ".sendAndReceive")) {
                        // Extract request topic
                        Pattern topicPattern = Pattern.compile("\\.sendAndReceive\\s*\\(\\s*[\"']([^\"']+)[\"']");
                        Matcher topicMatcher = topicPattern.matcher(lines[j]);
                        if (topicMatcher.find()) {
                            requestTopic = topicMatcher.group(1);
                        }

                        SpringReplyingTemplate replyingTemplate = new SpringReplyingTemplate();
                        replyingTemplate.setClassName(className);
                        replyingTemplate.setFilePath(filePath);
                        replyingTemplate.setPackageName(packageName);
                        replyingTemplate.setVariableName(variableName);
                        replyingTemplate.setRequestTopic(requestTopic);
                        replyingTemplate.setReplyTopic(replyTopic);
                        replyingTemplate.setLineNumber(j + 1);

                        replyingTemplates.add(replyingTemplate);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Extract listener container factory configurations
     */
    private void extractContainerFactories(String content, String filePath, String packageName, String className) {
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            // Look for factory bean definitions
            if (lines[i].contains("ContainerFactory")) {
                // Check if @Bean is nearby
                boolean hasBean = false;
                for (int j = Math.max(0, i - 5); j < i; j++) {
                    if (lines[j].contains("@Bean")) {
                        hasBean = true;
                        break;
                    }
                }

                if (hasBean) {
                    // Determine factory type
                    String factoryType = "unknown";
                    if (lines[i].contains("Kafka")) {
                        factoryType = "kafka";
                    } else if (lines[i].contains("Jms") || lines[i].contains("JMS")) {
                        factoryType = "jms";
                    } else if (lines[i].contains("Rabbit")) {
                        factoryType = "rabbitmq";
                    }

                    // Extract factory name from method
                    String factoryName = "Unknown";
                    for (int j = Math.max(0, i - 3); j < i + 3; j++) {
                        Pattern methodPattern = Pattern.compile("public\\s+\\w+\\s+(\\w+)\\s*\\(");
                        Matcher methodMatcher = methodPattern.matcher(lines[j]);
                        if (methodMatcher.find()) {
                            factoryName = methodMatcher.group(1);
                            break;
                        }
                    }

                    // Extract concurrency if present
                    String concurrency = null;
                    for (int j = i; j < Math.min(lines.length, i + 20); j++) {
                        Pattern concPattern = Pattern.compile("setConcurrency\\s*\\(\\s*[\"']([^\"']+)[\"']");
                        Matcher concMatcher = concPattern.matcher(lines[j]);
                        if (concMatcher.find()) {
                            concurrency = concMatcher.group(1);
                            break;
                        }
                    }

                    SpringContainerFactory factory = new SpringContainerFactory();
                    factory.setFactoryName(factoryName);
                    factory.setFactoryType(factoryType);
                    factory.setFilePath(filePath);
                    factory.setConcurrency(concurrency);
                    factory.setConfiguration(new HashMap<>());

                    containerFactories.add(factory);
                }
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

        // Note: YAML parsing would require additional library (SnakeYAML)
        // For now, we'll focus on properties files
    }

    /**
     * Parse application.properties for messaging config
     */
    private void parsePropertiesFile(Path propsFile) throws Exception {
        System.out.println("   Parsing: " + propsFile.getFileName());

        String relPath = rootDir.relativize(propsFile).toString();
        Map<String, String> kafkaProps = new HashMap<>();
        Map<String, String> rabbitProps = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(propsFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("spring.kafka.")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        kafkaProps.put(parts[0].trim(), parts[1].trim());
                    }
                } else if (line.startsWith("spring.rabbitmq.")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        rabbitProps.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }

        if (!kafkaProps.isEmpty()) {
            SpringMessagingConfig config = new SpringMessagingConfig();
            config.setConfigType("kafka");
            config.setBootstrapServers(kafkaProps.get("spring.kafka.bootstrap-servers"));
            config.setProperties(kafkaProps);
            config.setSourceFile(relPath);
            configs.add(config);
        }

        if (!rabbitProps.isEmpty()) {
            SpringMessagingConfig config = new SpringMessagingConfig();
            config.setConfigType("rabbitmq");
            config.setBootstrapServers(rabbitProps.get("spring.rabbitmq.host"));
            config.setProperties(rabbitProps);
            config.setSourceFile(relPath);
            configs.add(config);
        }
    }

    // Helper methods

    private String extractPackage(String content) {
        Pattern pattern = Pattern.compile("package\\s+([\\w.]+);");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractClassName(String content) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "Unknown";
    }

    private String getAnnotationBlock(String[] lines, int startLine) {
        StringBuilder block = new StringBuilder();
        int parenCount = 0;
        boolean started = false;

        for (int i = startLine; i < Math.min(lines.length, startLine + 10); i++) {
            String line = lines[i];
            if (line.contains("@")) {
                started = true;
            }
            if (started) {
                block.append(line).append(" ");
                parenCount += countChar(line, '(') - countChar(line, ')');
                if (parenCount == 0 && block.toString().contains("(")) {
                    break;
                }
            }
        }

        return block.toString();
    }

    private String extractAnnotationParam(String annotationBlock, String paramName) {
        // Try with quotes
        Pattern pattern = Pattern.compile(paramName + "\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(annotationBlock);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Try array notation
        pattern = Pattern.compile(paramName + "\\s*=\\s*\\{[\"']([^\"']+)[\"']");
        matcher = pattern.matcher(annotationBlock);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
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
                String msgType = "Unknown";
                if (params != null && !params.trim().isEmpty()) {
                    String[] paramParts = params.split(",");
                    if (paramParts.length > 0) {
                        String firstParam = paramParts[0].trim();
                        String[] typeParts = firstParam.split("\\s+");
                        if (typeParts.length > 0) {
                            msgType = typeParts[0];
                        }
                    }
                }

                String methodSig = returnType + " " + methodName + "(" + params + ")";
                return new MethodInfo(methodName, methodSig, msgType);
            }
        }

        return new MethodInfo("Unknown", "Unknown", "Unknown");
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

    private String inferMessageType(String[] lines, int lineNum) {
        for (int i = Math.max(0, lineNum - 10); i < lineNum; i++) {
            Pattern pattern = Pattern.compile("<(\\w+)>");
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "Unknown";
    }

    private String getCodeSnippet(String[] lines, int lineNum) {
        int start = Math.max(0, lineNum - 2);
        int end = Math.min(lines.length, lineNum + 3);
        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            snippet.append(lines[i]).append("\n");
        }
        return snippet.toString().trim();
    }

    private int countChar(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    /**
     * Consolidate destination information from all sources
     */
    private void consolidateDestinations() {
        Map<String, DestinationData> destMap = new HashMap<>();

        // From listeners
        for (SpringMessageListener listener : listeners) {
            String key = listener.getListenerType() + ":" + listener.getDestinationName();
            if (!destMap.containsKey(key)) {
                String destType = listener.getListenerType().equals("kafka") ? listener.getListenerType() + "-topic"
                        : listener.getListenerType() + "-queue";
                DestinationData data = new DestinationData();
                data.name = listener.getDestinationName();
                data.type = destType;
                data.files = new HashSet<>();
                data.config = new HashMap<>();
                data.source = "annotation";
                destMap.put(key, data);
            }
            destMap.get(key).files.add(listener.getFilePath());
        }

        // From producers
        for (SpringMessageProducer producer : producers) {
            String key = producer.getProducerType() + ":" + producer.getDestinationVariable();
            if (!destMap.containsKey(key)) {
                String destType = producer.getProducerType().equals("kafka") ? producer.getProducerType() + "-topic"
                        : producer.getProducerType() + "-queue";
                DestinationData data = new DestinationData();
                data.name = producer.getDestinationVariable();
                data.type = destType;
                data.files = new HashSet<>();
                data.config = new HashMap<>();
                data.source = "code";
                destMap.put(key, data);
            }
            destMap.get(key).files.add(producer.getFilePath());
        }

        // Convert to SpringDestination objects
        for (DestinationData data : destMap.values()) {
            SpringDestination destination = new SpringDestination();
            destination.setName(data.name);
            destination.setType(data.type);
            destination.setUsedInFiles(new ArrayList<>(data.files));
            destination.setConfiguration(data.config);
            destination.setDefinitionSource(data.source);
            destinations.add(destination);
        }
    }

    private void printExtractionSummary() {
        System.out.println("\n✅ Spring Messaging extraction complete:");
        System.out.println("   - " + destinations.size() + " Destinations");
        System.out.println("   - " + listeners.size() + " Message Listeners");
        System.out.println("   - " + producers.size() + " Message Producers");
        System.out.println("   - " + configs.size() + " Configuration Files");
        System.out.println("   - " + sendToAnnotations.size() + " @SendTo Annotations");
        System.out.println("   - " + replyingTemplates.size() + " ReplyingKafkaTemplates");
        System.out.println("   - " + containerFactories.size() + " Container Factories");
    }

    // Helper classes

    private static class MethodInfo {
        String name;
        String signature;
        String messageType;

        MethodInfo(String name, String signature, String messageType) {
            this.name = name;
            this.signature = signature;
            this.messageType = messageType;
        }
    }

    private static class DestinationData {
        String name;
        String type;
        Set<String> files;
        Map<String, String> config;
        String source;
    }
}

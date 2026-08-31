package com.migration.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model classes for Quarkus messaging metadata extraction.
 * These models represent messaging patterns detected in Quarkus applications.
 */
public class QuarkusMessagingModels {

    /**
     * Messaging Channel metadata
     */
    public static class MessagingChannel {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type; // 'incoming', 'outgoing', 'bidirectional'

        @JsonProperty("connector")
        private String connector; // 'smallrye-kafka', 'smallrye-amqp', 'smallrye-jms', etc.

        @JsonProperty("topic_or_queue")
        private String topicOrQueue;

        @JsonProperty("used_in_files")
        private List<String> usedInFiles = new ArrayList<>();

        @JsonProperty("configuration")
        private Map<String, String> configuration = new HashMap<>();

        @JsonProperty("definition_source")
        private String definitionSource; // 'annotation', 'config', 'code'

        public MessagingChannel() {
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getConnector() {
            return connector;
        }

        public void setConnector(String connector) {
            this.connector = connector;
        }

        public String getTopicOrQueue() {
            return topicOrQueue;
        }

        public void setTopicOrQueue(String topicOrQueue) {
            this.topicOrQueue = topicOrQueue;
        }

        public List<String> getUsedInFiles() {
            return usedInFiles;
        }

        public void setUsedInFiles(List<String> usedInFiles) {
            this.usedInFiles = usedInFiles;
        }

        public Map<String, String> getConfiguration() {
            return configuration;
        }

        public void setConfiguration(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        public String getDefinitionSource() {
            return definitionSource;
        }

        public void setDefinitionSource(String definitionSource) {
            this.definitionSource = definitionSource;
        }
    }

    /**
     * Message Consumer metadata (methods with @Incoming)
     */
    public static class MessageConsumer {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("package")
        private String packageName;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("acknowledgment_strategy")
        private String acknowledgmentStrategy;

        @JsonProperty("broadcast")
        private boolean broadcast;

        @JsonProperty("merge")
        private boolean merge;

        @JsonProperty("annotations")
        private List<String> annotations = new ArrayList<>();

        @JsonProperty("method_signature")
        private String methodSignature;

        @JsonProperty("return_type")
        private String returnType;

        @JsonProperty("consumer_style")
        private String consumerStyle; // 'reactive', 'imperative', 'blocking'

        @JsonProperty("line_number")
        private int lineNumber;

        public MessageConsumer() {
        }

        // Getters and setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannelName(String channelName) {
            this.channelName = channelName;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getAcknowledgmentStrategy() {
            return acknowledgmentStrategy;
        }

        public void setAcknowledgmentStrategy(String acknowledgmentStrategy) {
            this.acknowledgmentStrategy = acknowledgmentStrategy;
        }

        public boolean isBroadcast() {
            return broadcast;
        }

        public void setBroadcast(boolean broadcast) {
            this.broadcast = broadcast;
        }

        public boolean isMerge() {
            return merge;
        }

        public void setMerge(boolean merge) {
            this.merge = merge;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }

        public String getMethodSignature() {
            return methodSignature;
        }

        public void setMethodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public String getConsumerStyle() {
            return consumerStyle;
        }

        public void setConsumerStyle(String consumerStyle) {
            this.consumerStyle = consumerStyle;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Message Producer metadata (methods with @Outgoing or Emitter)
     */
    public static class MessageProducer {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("package")
        private String packageName;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("producer_type")
        private String producerType; // 'method', 'emitter', 'channel'

        @JsonProperty("annotations")
        private List<String> annotations = new ArrayList<>();

        @JsonProperty("method_signature")
        private String methodSignature;

        @JsonProperty("line_number")
        private int lineNumber;

        @JsonProperty("code_snippet")
        private String codeSnippet;

        public MessageProducer() {
        }

        // Getters and setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannelName(String channelName) {
            this.channelName = channelName;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getProducerType() {
            return producerType;
        }

        public void setProducerType(String producerType) {
            this.producerType = producerType;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }

        public String getMethodSignature() {
            return methodSignature;
        }

        public void setMethodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getCodeSnippet() {
            return codeSnippet;
        }

        public void setCodeSnippet(String codeSnippet) {
            this.codeSnippet = codeSnippet;
        }
    }

    /**
     * Emitter injection for programmatic message sending
     */
    public static class EmitterInjection {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("variable_name")
        private String variableName;

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("emitter_type")
        private String emitterType; // 'Emitter', 'MutinyEmitter', 'Channel'

        @JsonProperty("line_number")
        private int lineNumber;

        public EmitterInjection() {
        }

        // Getters and setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getVariableName() {
            return variableName;
        }

        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannelName(String channelName) {
            this.channelName = channelName;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getEmitterType() {
            return emitterType;
        }

        public void setEmitterType(String emitterType) {
            this.emitterType = emitterType;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Vert.x Event Bus usage
     */
    public static class VertxEventBus {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("address")
        private String address;

        @JsonProperty("operation")
        private String operation; // 'send', 'publish', 'consumer', 'request'

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("line_number")
        private int lineNumber;

        public VertxEventBus() {
        }

        // Getters and setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * CDI Event usage metadata
     */
    public static class CDIEvent {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("event_type")
        private String eventType;

        @JsonProperty("operation")
        private String operation; // 'observe' or 'fire'

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("line_number")
        private int lineNumber;

        public CDIEvent() {
        }

        // Getters and setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Connector configuration
     */
    public static class ConnectorConfiguration {
        @JsonProperty("connector_name")
        private String connectorName;

        @JsonProperty("connector_type")
        private String connectorType;

        @JsonProperty("channels")
        private List<String> channels = new ArrayList<>();

        @JsonProperty("properties")
        private Map<String, String> properties = new HashMap<>();

        @JsonProperty("source")
        private String source; // 'application.properties' or 'application.yml'

        public ConnectorConfiguration() {
        }

        // Getters and setters
        public String getConnectorName() {
            return connectorName;
        }

        public void setConnectorName(String connectorName) {
            this.connectorName = connectorName;
        }

        public String getConnectorType() {
            return connectorType;
        }

        public void setConnectorType(String connectorType) {
            this.connectorType = connectorType;
        }

        public List<String> getChannels() {
            return channels;
        }

        public void setChannels(List<String> channels) {
            this.channels = channels;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }

    /**
     * Native Kafka client usage metadata
     */
    public static class KafkaClientUsage {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("client_type")
        private String clientType; // "producer" or "consumer"

        @JsonProperty("variable_name")
        private String variableName;

        @JsonProperty("generic_type")
        private String genericType;

        @JsonProperty("topics")
        private List<String> topics = new ArrayList<>();

        @JsonProperty("line_number")
        private int lineNumber;

        public KafkaClientUsage() {
        }

        // Getters and Setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getClientType() {
            return clientType;
        }

        public void setClientType(String clientType) {
            this.clientType = clientType;
        }

        public String getVariableName() {
            return variableName;
        }

        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        public String getGenericType() {
            return genericType;
        }

        public void setGenericType(String genericType) {
            this.genericType = genericType;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Reactive return type usage (Uni<Message<T>>, Multi<Message<T>>)
     */
    public static class ReactiveReturnType {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("return_type")
        private String returnType; // "Uni<Message<T>>" or "Multi<Message<T>>"

        @JsonProperty("reactive_type")
        private String reactiveType;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("annotation")
        private String annotation;

        @JsonProperty("line_number")
        private int lineNumber;

        public ReactiveReturnType() {
        }

        // Getters and Setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public String getReactiveType() {
            return reactiveType;
        }

        public void setReactiveType(String reactiveType) {
            this.reactiveType = reactiveType;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannel(String channelName) {
            this.channelName = channelName;
        }

        public String getAnnotation() {
            return annotation;
        }

        public void setAnnotation(String annotation) {
            this.annotation = annotation;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Manual acknowledgment/nack handler usage
     */
    public static class AcknowledgmentHandler {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("operation")
        private String operation; // "ack" or "nack"

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("has_ack")
        private boolean hasAck;

        @JsonProperty("has_nack")
        private boolean hasNack;

        @JsonProperty("line_number")
        private int lineNumber;

        public AcknowledgmentHandler() {
        }

        // Getters and Setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannel(String channelName) {
            this.channelName = channelName;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public boolean isHasAck() {
            return hasAck;
        }

        public void setHasAck(boolean hasAck) {
            this.hasAck = hasAck;
        }

        public boolean isHasNack() {
            return hasNack;
        }

        public void setHasNack(boolean hasNack) {
            this.hasNack = hasNack;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Batch message consumer (@Incoming with List<Message<T>>)
     */
    public static class BatchConsumer {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("channel_name")
        private String channelName;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("parameter_name")
        private String parameterName;

        @JsonProperty("batch_size")
        private int batchSize;

        @JsonProperty("line_number")
        private int lineNumber;

        public BatchConsumer() {
        }

        // Getters and Setters
        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannel(String channelName) {
            this.channelName = channelName;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getParameterName() {
            return parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Kafka-specific configuration
     */
    public static class KafkaConfiguration {
        @JsonProperty("bootstrap_servers")
        private String bootstrapServers;

        @JsonProperty("group_id")
        private String groupId;

        @JsonProperty("key_serializer")
        private String keySerializer;

        @JsonProperty("value_serializer")
        private String valueSerializer;

        @JsonProperty("key_deserializer")
        private String keyDeserializer;

        @JsonProperty("value_deserializer")
        private String valueDeserializer;

        @JsonProperty("auto_offset_reset")
        private String autoOffsetReset;

        @JsonProperty("enable_auto_commit")
        private String enableAutoCommit;

        @JsonProperty("consumer_properties")
        private Map<String, String> consumerProperties = new HashMap<>();

        @JsonProperty("producer_properties")
        private Map<String, String> producerProperties = new HashMap<>();

        @JsonProperty("additional_properties")
        private Map<String, String> additionalProperties = new HashMap<>();

        @JsonProperty("source_file")
        private String sourceFile;

        public KafkaConfiguration() {
        }

        // Getters and Setters
        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getKeySerializer() {
            return keySerializer;
        }

        public void setKeySerializer(String keySerializer) {
            this.keySerializer = keySerializer;
        }

        public String getValueSerializer() {
            return valueSerializer;
        }

        public void setValueSerializer(String valueSerializer) {
            this.valueSerializer = valueSerializer;
        }

        public String getKeyDeserializer() {
            return keyDeserializer;
        }

        public void setKeyDeserializer(String keyDeserializer) {
            this.keyDeserializer = keyDeserializer;
        }

        public String getValueDeserializer() {
            return valueDeserializer;
        }

        public void setValueDeserializer(String valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        public String getEnableAutoCommit() {
            return enableAutoCommit;
        }

        public void setEnableAutoCommit(String enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        public Map<String, String> getConsumerProperties() {
            return consumerProperties;
        }

        public void setConsumerProperties(Map<String, String> consumerProperties) {
            this.consumerProperties = consumerProperties;
        }

        public Map<String, String> getProducerProperties() {
            return producerProperties;
        }

        public void setProducerProperties(Map<String, String> producerProperties) {
            this.producerProperties = producerProperties;
        }

        public Map<String, String> getAdditionalProperties() {
            return additionalProperties;
        }

        public void setAdditionalProperties(Map<String, String> additionalProperties) {
            this.additionalProperties = additionalProperties;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }
    }

    /**
     * AMQP/RabbitMQ configuration
     */
    public static class AMQPConfiguration {
        @JsonProperty("host")
        private String host;

        @JsonProperty("port")
        private Integer port;

        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        private String password;

        @JsonProperty("virtual_host")
        private String virtualHost;

        @JsonProperty("durable")
        private String durable;

        @JsonProperty("auto_acknowledge")
        private String autoAcknowledge;

        @JsonProperty("properties")
        private Map<String, String> properties = new HashMap<>();

        @JsonProperty("additional_properties")
        private Map<String, String> additionalProperties = new HashMap<>();

        @JsonProperty("source_file")
        private String sourceFile;

        public AMQPConfiguration() {
        }

        // Getters and Setters
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getVirtualHost() {
            return virtualHost;
        }

        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }

        public String getDurable() {
            return durable;
        }

        public void setDurable(String durable) {
            this.durable = durable;
        }

        public String getAutoAcknowledge() {
            return autoAcknowledge;
        }

        public void setAutoAcknowledge(String autoAcknowledge) {
            this.autoAcknowledge = autoAcknowledge;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public Map<String, String> getAdditionalProperties() {
            return additionalProperties;
        }

        public void setAdditionalProperties(Map<String, String> additionalProperties) {
            this.additionalProperties = additionalProperties;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }
    }

    /**
     * Root container for all Quarkus messaging metadata
     */
    public static class QuarkusMessagingMetadata {
        @JsonProperty("channels")
        private List<MessagingChannel> channels = new ArrayList<>();

        @JsonProperty("consumers")
        private List<MessageConsumer> consumers = new ArrayList<>();

        @JsonProperty("producers")
        private List<MessageProducer> producers = new ArrayList<>();

        @JsonProperty("emitters")
        private List<EmitterInjection> emitters = new ArrayList<>();

        @JsonProperty("eventbus_usages")
        private List<VertxEventBus> eventbusUsages = new ArrayList<>();

        @JsonProperty("cdi_events")
        private List<CDIEvent> cdiEvents = new ArrayList<>();

        @JsonProperty("connectors")
        private List<ConnectorConfiguration> connectors = new ArrayList<>();

        @JsonProperty("kafka_client_usages")
        private List<KafkaClientUsage> kafkaClientUsages = new ArrayList<>();

        @JsonProperty("reactive_return_types")
        private List<ReactiveReturnType> reactiveReturnTypes = new ArrayList<>();

        @JsonProperty("ack_handlers")
        private List<AcknowledgmentHandler> ackHandlers = new ArrayList<>();

        @JsonProperty("batch_consumers")
        private List<BatchConsumer> batchConsumers = new ArrayList<>();

        @JsonProperty("kafka_config")
        private KafkaConfiguration kafkaConfig;

        @JsonProperty("amqp_config")
        private AMQPConfiguration amqpConfig;

        public QuarkusMessagingMetadata() {
        }

        // Getters and setters
        public List<MessagingChannel> getChannels() {
            return channels;
        }

        public void setChannels(List<MessagingChannel> channels) {
            this.channels = channels;
        }

        public List<MessageConsumer> getConsumers() {
            return consumers;
        }

        public void setConsumers(List<MessageConsumer> consumers) {
            this.consumers = consumers;
        }

        public List<MessageProducer> getProducers() {
            return producers;
        }

        public void setProducers(List<MessageProducer> producers) {
            this.producers = producers;
        }

        public List<EmitterInjection> getEmitters() {
            return emitters;
        }

        public void setEmitters(List<EmitterInjection> emitters) {
            this.emitters = emitters;
        }

        public List<VertxEventBus> getEventbusUsages() {
            return eventbusUsages;
        }

        public void setEventbusUsages(List<VertxEventBus> eventbusUsages) {
            this.eventbusUsages = eventbusUsages;
        }

        public List<CDIEvent> getCdiEvents() {
            return cdiEvents;
        }

        public void setCdiEvents(List<CDIEvent> cdiEvents) {
            this.cdiEvents = cdiEvents;
        }

        public List<ConnectorConfiguration> getConnectors() {
            return connectors;
        }

        public void setConnectors(List<ConnectorConfiguration> connectors) {
            this.connectors = connectors;
        }

        public List<KafkaClientUsage> getKafkaClientUsages() {
            return kafkaClientUsages;
        }

        public void setKafkaClientUsages(List<KafkaClientUsage> kafkaClientUsages) {
            this.kafkaClientUsages = kafkaClientUsages;
        }

        public List<ReactiveReturnType> getReactiveReturnTypes() {
            return reactiveReturnTypes;
        }

        public void setReactiveReturnTypes(List<ReactiveReturnType> reactiveReturnTypes) {
            this.reactiveReturnTypes = reactiveReturnTypes;
        }

        public List<AcknowledgmentHandler> getAckHandlers() {
            return ackHandlers;
        }

        public void setAckHandlers(List<AcknowledgmentHandler> ackHandlers) {
            this.ackHandlers = ackHandlers;
        }

        public List<BatchConsumer> getBatchConsumers() {
            return batchConsumers;
        }

        public void setBatchConsumers(List<BatchConsumer> batchConsumers) {
            this.batchConsumers = batchConsumers;
        }

        public KafkaConfiguration getKafkaConfig() {
            return kafkaConfig;
        }

        public void setKafkaConfig(KafkaConfiguration kafkaConfig) {
            this.kafkaConfig = kafkaConfig;
        }

        public AMQPConfiguration getAmqpConfig() {
            return amqpConfig;
        }

        public void setAmqpConfig(AMQPConfiguration amqpConfig) {
            this.amqpConfig = amqpConfig;
        }
    }
}

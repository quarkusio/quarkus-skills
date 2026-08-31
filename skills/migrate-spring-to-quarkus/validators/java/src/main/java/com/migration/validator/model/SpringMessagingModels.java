package com.migration.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model classes for Spring messaging metadata extraction.
 * These models represent messaging patterns detected in Spring applications.
 */
public class SpringMessagingModels {

    /**
     * Spring messaging destination metadata
     */
    public static class SpringDestination {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type; // 'kafka-topic', 'rabbitmq-queue', 'rabbitmq-exchange', 'jms-queue',
                             // 'jms-topic'

        @JsonProperty("used_in_files")
        private List<String> usedInFiles = new ArrayList<>();

        @JsonProperty("configuration")
        private Map<String, String> configuration = new HashMap<>();

        @JsonProperty("definition_source")
        private String definitionSource; // 'annotation', 'config', 'code'

        public SpringDestination() {
        }

        public SpringDestination(String name, String type, List<String> usedInFiles,
                Map<String, String> configuration, String definitionSource) {
            this.name = name;
            this.type = type;
            this.usedInFiles = usedInFiles != null ? usedInFiles : new ArrayList<>();
            this.configuration = configuration != null ? configuration : new HashMap<>();
            this.definitionSource = definitionSource;
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
     * Spring message listener metadata
     */
    public static class SpringMessageListener {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("package")
        private String packageName;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("listener_type")
        private String listenerType; // 'kafka', 'rabbitmq', 'jms', 'stream'

        @JsonProperty("destination_name")
        private String destinationName;

        @JsonProperty("group_id")
        private String groupId;

        @JsonProperty("concurrency")
        private String concurrency;

        @JsonProperty("annotations")
        private List<String> annotations = new ArrayList<>();

        @JsonProperty("method_signature")
        private String methodSignature;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("line_number")
        private int lineNumber;

        @JsonProperty("container_factory")
        private String containerFactory;

        public SpringMessageListener() {
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

        public String getListenerType() {
            return listenerType;
        }

        public void setListenerType(String listenerType) {
            this.listenerType = listenerType;
        }

        public String getDestinationName() {
            return destinationName;
        }

        public void setDestinationName(String destinationName) {
            this.destinationName = destinationName;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(String concurrency) {
            this.concurrency = concurrency;
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

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getContainerFactory() {
            return containerFactory;
        }

        public void setContainerFactory(String containerFactory) {
            this.containerFactory = containerFactory;
        }
    }

    /**
     * Spring message producer metadata
     */
    public static class SpringMessageProducer {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("package")
        private String packageName;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("producer_type")
        private String producerType; // 'kafka', 'rabbitmq', 'jms', 'stream'

        @JsonProperty("destination_variable")
        private String destinationVariable;

        @JsonProperty("message_type")
        private String messageType;

        @JsonProperty("line_number")
        private int lineNumber;

        @JsonProperty("template_variable")
        private String templateVariable;

        @JsonProperty("code_snippet")
        private String codeSnippet;

        public SpringMessageProducer() {
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

        public String getProducerType() {
            return producerType;
        }

        public void setProducerType(String producerType) {
            this.producerType = producerType;
        }

        public String getDestinationVariable() {
            return destinationVariable;
        }

        public void setDestinationVariable(String destinationVariable) {
            this.destinationVariable = destinationVariable;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getTemplateVariable() {
            return templateVariable;
        }

        public void setTemplateVariable(String templateVariable) {
            this.templateVariable = templateVariable;
        }

        public String getCodeSnippet() {
            return codeSnippet;
        }

        public void setCodeSnippet(String codeSnippet) {
            this.codeSnippet = codeSnippet;
        }
    }

    /**
     * Spring ReplyingKafkaTemplate usage
     */
    public static class SpringReplyingTemplate {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("package")
        private String packageName;

        @JsonProperty("variable_name")
        private String variableName;

        @JsonProperty("request_topic")
        private String requestTopic;

        @JsonProperty("reply_topic")
        private String replyTopic;

        @JsonProperty("line_number")
        private int lineNumber;

        public SpringReplyingTemplate() {
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

        public String getVariableName() {
            return variableName;
        }

        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        public String getRequestTopic() {
            return requestTopic;
        }

        public void setRequestTopic(String requestTopic) {
            this.requestTopic = requestTopic;
        }

        public String getReplyTopic() {
            return replyTopic;
        }

        public void setReplyTopic(String replyTopic) {
            this.replyTopic = replyTopic;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * @SendTo annotation for request-reply patterns
     */
    public static class SpringSendTo {
        @JsonProperty("class_name")
        private String className;

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("method_name")
        private String methodName;

        @JsonProperty("send_to_destination")
        private String sendToDestination;

        @JsonProperty("listener_destination")
        private String listenerDestination;

        @JsonProperty("line_number")
        private int lineNumber;

        public SpringSendTo() {
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

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getSendToDestination() {
            return sendToDestination;
        }

        public void setSendToDestination(String sendToDestination) {
            this.sendToDestination = sendToDestination;
        }

        public String getListenerDestination() {
            return listenerDestination;
        }

        public void setListenerDestination(String listenerDestination) {
            this.listenerDestination = listenerDestination;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Spring listener container factory configuration
     */
    public static class SpringContainerFactory {
        @JsonProperty("factory_name")
        private String factoryName;

        @JsonProperty("factory_type")
        private String factoryType; // 'kafka', 'jms', 'rabbitmq'

        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("concurrency")
        private String concurrency;

        @JsonProperty("configuration")
        private Map<String, String> configuration = new HashMap<>();

        public SpringContainerFactory() {
        }

        // Getters and setters
        public String getFactoryName() {
            return factoryName;
        }

        public void setFactoryName(String factoryName) {
            this.factoryName = factoryName;
        }

        public String getFactoryType() {
            return factoryType;
        }

        public void setFactoryType(String factoryType) {
            this.factoryType = factoryType;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(String concurrency) {
            this.concurrency = concurrency;
        }

        public Map<String, String> getConfiguration() {
            return configuration;
        }

        public void setConfiguration(Map<String, String> configuration) {
            this.configuration = configuration;
        }
    }

    /**
     * Spring messaging configuration
     */
    public static class SpringMessagingConfig {
        @JsonProperty("config_type")
        private String configType; // 'kafka', 'rabbitmq', 'jms'

        @JsonProperty("bootstrap_servers")
        private String bootstrapServers;

        @JsonProperty("properties")
        private Map<String, String> properties = new HashMap<>();

        @JsonProperty("source_file")
        private String sourceFile;

        public SpringMessagingConfig() {
        }

        // Getters and setters
        public String getConfigType() {
            return configType;
        }

        public void setConfigType(String configType) {
            this.configType = configType;
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }
    }

    /**
     * Root container for all Spring messaging metadata
     */
    public static class SpringMessagingMetadata {
        @JsonProperty("destinations")
        private List<SpringDestination> destinations = new ArrayList<>();

        @JsonProperty("listeners")
        private List<SpringMessageListener> listeners = new ArrayList<>();

        @JsonProperty("producers")
        private List<SpringMessageProducer> producers = new ArrayList<>();

        @JsonProperty("replying_templates")
        private List<SpringReplyingTemplate> replyingTemplates = new ArrayList<>();

        @JsonProperty("send_to_annotations")
        private List<SpringSendTo> sendToAnnotations = new ArrayList<>();

        @JsonProperty("container_factories")
        private List<SpringContainerFactory> containerFactories = new ArrayList<>();

        @JsonProperty("configs")
        private List<SpringMessagingConfig> configs = new ArrayList<>();

        public SpringMessagingMetadata() {
        }

        // Getters and setters
        public List<SpringDestination> getDestinations() {
            return destinations;
        }

        public void setDestinations(List<SpringDestination> destinations) {
            this.destinations = destinations;
        }

        public List<SpringMessageListener> getListeners() {
            return listeners;
        }

        public void setListeners(List<SpringMessageListener> listeners) {
            this.listeners = listeners;
        }

        public List<SpringMessageProducer> getProducers() {
            return producers;
        }

        public void setProducers(List<SpringMessageProducer> producers) {
            this.producers = producers;
        }

        public List<SpringReplyingTemplate> getReplyingTemplates() {
            return replyingTemplates;
        }

        public void setReplyingTemplates(List<SpringReplyingTemplate> replyingTemplates) {
            this.replyingTemplates = replyingTemplates;
        }

        public List<SpringSendTo> getSendToAnnotations() {
            return sendToAnnotations;
        }

        public void setSendToAnnotations(List<SpringSendTo> sendToAnnotations) {
            this.sendToAnnotations = sendToAnnotations;
        }

        public List<SpringContainerFactory> getContainerFactories() {
            return containerFactories;
        }

        public void setContainerFactories(List<SpringContainerFactory> containerFactories) {
            this.containerFactories = containerFactories;
        }

        public List<SpringMessagingConfig> getConfigs() {
            return configs;
        }

        public void setConfigs(List<SpringMessagingConfig> configs) {
            this.configs = configs;
        }
    }
}

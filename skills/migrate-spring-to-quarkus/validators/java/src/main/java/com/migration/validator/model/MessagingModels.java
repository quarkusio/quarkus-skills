package com.migration.validator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Model classes for messaging metadata (Spring and Quarkus)
 * Used for validating messaging migration from Spring to Quarkus Reactive
 * Messaging
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessagingModels {

    /**
     * Spring messaging destination (topic/queue)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpringDestination {
        @JsonProperty("name")
        public String name;

        @JsonProperty("type")
        public String type; // "topic", "queue", "exchange"

        @JsonProperty("used_in_files")
        public List<String> usedInFiles;

        @JsonProperty("configuration")
        public Object configuration;
    }

    /**
     * Spring message listener (@KafkaListener, @JmsListener, @RabbitListener)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpringMessageListener {
        @JsonProperty("class_name")
        public String className;

        @JsonProperty("method_name")
        public String methodName;

        @JsonProperty("listener_type")
        public String listenerType; // "kafka", "jms", "rabbitmq"

        @JsonProperty("destination_name")
        public String destinationName;

        @JsonProperty("concurrency")
        public String concurrency;

        @JsonProperty("group_id")
        public String groupId;

        @JsonProperty("file_path")
        public String filePath;

        @JsonProperty("line_number")
        public Integer lineNumber;
    }

    /**
     * Spring message producer (KafkaTemplate, JmsTemplate, RabbitTemplate)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpringMessageProducer {
        @JsonProperty("class_name")
        public String className;

        @JsonProperty("method_name")
        public String methodName;

        @JsonProperty("producer_type")
        public String producerType; // "kafka", "jms", "rabbitmq"

        @JsonProperty("destination_variable")
        public String destinationVariable;

        @JsonProperty("message_type")
        public String messageType;

        @JsonProperty("file_path")
        public String filePath;

        @JsonProperty("line_number")
        public Integer lineNumber;
    }

    /**
     * Quarkus messaging channel
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuarkusMessagingChannel {
        @JsonProperty("name")
        public String name;

        @JsonProperty("direction")
        public String direction; // "incoming", "outgoing"

        @JsonProperty("type")
        public String type; // Alternative field name for direction

        @JsonProperty("connector")
        public String connector; // "smallrye-kafka", "smallrye-jms", "smallrye-in-memory"

        @JsonProperty("topic_or_queue")
        public String topicOrQueue;

        @JsonProperty("configuration")
        public Object configuration;
    }

    /**
     * Quarkus message consumer (@Incoming)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuarkusMessageConsumer {
        @JsonProperty("class_name")
        public String className;

        @JsonProperty("method_name")
        public String methodName;

        @JsonProperty("channel_name")
        public String channelName;

        @JsonProperty("message_type")
        public String messageType;

        @JsonProperty("file_path")
        public String filePath;

        @JsonProperty("line_number")
        public Integer lineNumber;
    }

    /**
     * Quarkus message producer (@Outgoing or Emitter)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuarkusMessageProducer {
        @JsonProperty("class_name")
        public String className;

        @JsonProperty("method_name")
        public String methodName;

        @JsonProperty("producer_type")
        public String producerType; // "outgoing", "emitter"

        @JsonProperty("channel_name")
        public String channelName;

        @JsonProperty("message_type")
        public String messageType;

        @JsonProperty("file_path")
        public String filePath;

        @JsonProperty("line_number")
        public Integer lineNumber;
    }

    /**
     * Spring messaging metadata container
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpringMessagingMetadata {
        @JsonProperty("project_root")
        public String projectRoot;

        @JsonProperty("destinations")
        public List<SpringDestination> destinations;

        @JsonProperty("message_listeners")
        public List<SpringMessageListener> messageListeners;

        @JsonProperty("message_producers")
        public List<SpringMessageProducer> messageProducers;

        @JsonProperty("configuration")
        public Object configuration;
    }

    /**
     * Quarkus messaging metadata container
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuarkusMessagingMetadata {
        @JsonProperty("project_root")
        public String projectRoot;

        @JsonProperty("messaging_channels")
        public List<QuarkusMessagingChannel> messagingChannels;

        @JsonProperty("channels")
        public List<QuarkusMessagingChannel> channels; // Alternative field name

        @JsonProperty("message_consumers")
        public List<QuarkusMessageConsumer> messageConsumers;

        @JsonProperty("message_producers")
        public List<QuarkusMessageProducer> messageProducers;

        @JsonProperty("configuration")
        public Object configuration;
    }
}

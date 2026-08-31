---
name: messaging-migration-agent
description: Phase 7 Messaging Migration Agent. Converts Spring Kafka/RabbitMQ/JMS listeners and producers to Quarkus SmallRye Reactive Messaging.
  Migrates @KafkaListener/@RabbitListener/@JmsListener to @Incoming and validates with MessagingValidator.
license: Apache-2.0
metadata:
  phase: 7
  agent_type: migration
---

# Phase 7 — Messaging Migration Agent

> **Execution mode:** This module applies deterministic listener and producer transformations and
> contains no user decision gates. It reads `execution.mode` from `migration-spec.yaml` only for
> error handling: on a compile failure it delegates to
> [`modules/testing/compile-fix.md`](../testing/compile-fix.md) (3 retries per file); if still
> failing, **`interactive`** pauses and asks the user, **`autonomous`** records the failure under
> `unresolved_issues:` and continues — no prompts in either path.

## Purpose

Convert Spring messaging (Kafka, RabbitMQ, JMS) to Quarkus SmallRye Reactive Messaging.

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-07-messaging-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- migration-spec.yaml (messaging_listeners and messaging_producers lists)
- Source messaging files

## Transformation Rules

Apply RULE GROUP 5 from transformation_rules.md.

### Kafka Listener Migration

```java
// Before (Spring Kafka)
@Component
public class OrderListener {
    @KafkaListener(topics = "orders", groupId = "order-service")
    public void processOrder(Order order) {
        // process order
    }
}

// After (Quarkus Reactive Messaging)
@ApplicationScoped
public class OrderProcessor {
    @Incoming("orders")
    public void processOrder(Order order) {
        // process order
    }
}
```

### Kafka Producer Migration

```java
// Before (Spring Kafka)
@Service
public class OrderProducer {
    @Autowired
    private KafkaTemplate<String, Order> kafkaTemplate;
    
    public void sendOrder(Order order) {
        kafkaTemplate.send("orders", order);
    }
}

// After (Quarkus Reactive Messaging)
@ApplicationScoped
public class OrderProducer {
    @Channel("orders")
    Emitter<Order> orderEmitter;
    
    public void sendOrder(Order order) {
        orderEmitter.send(order);
    }
}
```

### RabbitMQ Listener Migration

```java
// Before (Spring AMQP)
@Component
public class OrderListener {
    @RabbitListener(queues = "orders")
    public void processOrder(Order order) {
        // process order
    }
}

// After (Quarkus Reactive Messaging)
@ApplicationScoped
public class OrderProcessor {
    @Incoming("orders")
    public void processOrder(Order order) {
        // process order
    }
}
```

### JMS Listener Migration

```java
// Before (Spring JMS)
@Component
public class OrderListener {
    @JmsListener(destination = "orders")
    public void processOrder(Order order) {
        // process order
    }
}

// After (Quarkus Reactive Messaging)
@ApplicationScoped
public class OrderProcessor {
    @Incoming("orders")
    public void processOrder(Order order) {
        // process order
    }
}
```

## Configuration Migration

### Kafka Configuration

```properties
# Before (Spring)
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=order-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# After (Quarkus)
kafka.bootstrap.servers=localhost:9092

# Incoming channel
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.incoming.orders.group.id=order-service
mp.messaging.incoming.orders.auto.offset.reset=earliest
mp.messaging.incoming.orders.value.deserializer=io.quarkus.kafka.client.serialization.ObjectMapperDeserializer

# Outgoing channel
mp.messaging.outgoing.orders.connector=smallrye-kafka
mp.messaging.outgoing.orders.topic=orders
mp.messaging.outgoing.orders.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer
```

### RabbitMQ Configuration

```properties
# Before (Spring)
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# After (Quarkus)
rabbitmq-host=localhost
rabbitmq-port=5672
rabbitmq-username=guest
rabbitmq-password=guest

mp.messaging.incoming.orders.connector=smallrye-amqp
mp.messaging.incoming.orders.queue.name=orders

mp.messaging.outgoing.orders.connector=smallrye-amqp
mp.messaging.outgoing.orders.address=orders
```
## CRITICAL: Channel Naming Rules

**⚠️ IMPORTANT: SmallRye Reactive Messaging does NOT allow the same channel name for both @Incoming and @Outgoing.**

This rule applies to **ALL connectors**: Kafka, AMQP/RabbitMQ, JMS/Artemis, in-memory, etc.

### The Problem
If you configure the same channel name for both incoming and outgoing, you will get this error:
```
SRMSG00073: Invalid configuration, the following channel names cannot be used for both incoming and outgoing: [channel-name]
```

### The Solution
Use different channel names for producers (outgoing) and consumers (incoming), even if they use the same underlying destination (topic/queue/address).

**WRONG - Will cause error (applies to any connector):**

```properties
# Kafka example - WRONG
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.outgoing.orders.connector=smallrye-kafka  # ERROR: Same channel name!
mp.messaging.outgoing.orders.topic=orders

# AMQP example - WRONG
mp.messaging.incoming.events.connector=smallrye-amqp
mp.messaging.incoming.events.address=EventQueue
mp.messaging.outgoing.events.connector=smallrye-amqp  # ERROR: Same channel name!
mp.messaging.outgoing.events.address=EventQueue

# In-memory example - WRONG
mp.messaging.incoming.data.connector=smallrye-in-memory
mp.messaging.incoming.data.channel-name=data-stream
mp.messaging.outgoing.data.connector=smallrye-in-memory  # ERROR: Same channel name!
mp.messaging.outgoing.data.channel-name=data-stream
```

**CORRECT - Different channel names (works with any connector):**

```properties
# Kafka example - CORRECT
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.outgoing.orders-out.connector=smallrye-kafka  # Different channel name
mp.messaging.outgoing.orders-out.topic=orders              # Same topic is OK

# AMQP example - CORRECT
mp.messaging.incoming.events.connector=smallrye-amqp
mp.messaging.incoming.events.address=EventQueue
mp.messaging.outgoing.events-out.connector=smallrye-amqp   # Different channel name
mp.messaging.outgoing.events-out.address=EventQueue        # Same address is OK

# In-memory example - CORRECT
mp.messaging.incoming.data.connector=smallrye-in-memory
mp.messaging.incoming.data.channel-name=data-stream
mp.messaging.outgoing.data-out.connector=smallrye-in-memory  # Different channel name
mp.messaging.outgoing.data-out.channel-name=data-stream      # Same stream is OK
```

**Java Code Pattern (same for all connectors):**
```java
// Consumer uses original channel name
@Incoming("orders")  // or "events" or "data"
public void processMessage(Message msg) { }

// Producer uses different channel name with -out suffix
@Channel("orders-out")  // or "events-out" or "data-out"
Emitter<Message> messageEmitter;
```

### Naming Convention
Recommended pattern: Add `-out` suffix to outgoing channel names:
- Incoming: `orders`, `events`, `notifications`, `cargo-handled`
- Outgoing: `orders-out`, `events-out`, `notifications-out`, `cargo-handled-out`

This keeps the relationship clear while avoiding conflicts across all messaging technologies.

## Import Updates

Remove:
```java
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
```

Add:
```java
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
```

## Steps

1. Read migration-spec.yaml messaging configuration
2. For each listener:
   - Convert @KafkaListener/@RabbitListener/@JmsListener to @Incoming
   - Extract topic/queue name as channel name
   - Update class to @ApplicationScoped
   - Record in transformation ledger
3. For each producer:
   - Convert KafkaTemplate/RabbitTemplate/JmsTemplate to @Channel Emitter
   - Update send methods
   - Record in transformation ledger
4. Update application.properties with messaging configuration
5. Run `mvn clean package -DskipTests` to ensure compilation is successful
6. Generate messaging-migration-report.json

## Advanced Patterns

### Reactive Processing

```java
// Reactive processing with Uni
@Incoming("orders")
@Outgoing("processed-orders")
public Uni<ProcessedOrder> processOrder(Order order) {
    return Uni.createFrom().item(() -> process(order));
}
```

### Message Acknowledgment

```java
// Manual acknowledgment
@Incoming("orders")
public CompletionStage<Void> processOrder(Message<Order> message) {
    Order order = message.getPayload();
    // process order
    return message.ack();
}
```
## Validation Gate

After completing all transformations, **MANDATORY VALIDATION** must be performed:

### 1. Generate Messaging Metadata and Run Validator

**Run validator after completing messaging migration:**

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java
mvn clean package -DskipTests -q

# Extract messaging metadata
java -jar target/migration-validator-1.0.0.jar extract spring-messaging \
  <spring_source_dir> <spring_source_dir>/migration-metadata/spring-messaging.json
java -jar target/migration-validator-1.0.0.jar extract quarkus-messaging \
  <quarkus_target_dir> <quarkus_target_dir>/migration-metadata/quarkus-messaging.json

# Run validator
java -jar target/migration-validator-1.0.0.jar validate messaging \
  <spring_source_dir>/migration-metadata/spring-messaging.json \
  <quarkus_target_dir>/migration-metadata/quarkus-messaging.json \
  <quarkus_target_dir> \
  <quarkus_target_dir>/migration-spec.yaml
```

**VALIDATION LOOP (MANDATORY - DO NOT SKIP):**
- If validator shows failures (exit code 1):
  1. Read error messages and identify issues
  2. Fix the problems in messaging code/config
  3. Regenerate metadata and rerun validator
  4. Repeat until exit code = 0 and Status = SUCCESS
- Only proceed to next phase when: `Rules: X total | X passed | 0 failed`

**Validator checks:** Destination coverage, consumer mapping (@KafkaListener→@Incoming), producer mapping (KafkaTemplate→Emitter), message types, configuration, no orphaned channels
    total_checks: 8
    critical_failures: 0
```

### Blocking Criteria
The following issues will block progression to Phase 8:
- **Destination Coverage Failure** - Spring destinations without Quarkus equivalents
- **Consumer Mapping Failure** - Listeners not migrated to @Incoming
- **Producer Mapping Failure** - Producers not migrated to Emitter/@Outgoing
- **Message Type Mismatch** - Message types changed during migration
- **Missing Configuration** - Channels not configured in application.properties

### Non-Blocking Warnings
These can be addressed later but should be documented:
- Producer count differences (may indicate refactoring)
- Channel naming conventions (verify intentional)
- Optional messaging features not migrated
- Advanced patterns not detected (e.g., @SendTo, ReplyingKafkaTemplate)

### Advanced Pattern Detection
The validator also detects advanced patterns:
- **Spring**: @SendTo, ReplyingKafkaTemplate, Container Factories
- **Quarkus**: Uni<Message<T>>, ack/nack handlers, batch consumers

**⚠️ IMPORTANT: Do not proceed to Phase 8 until validation gate passes!**


## Output

**Directory Setup:**
```bash
mkdir -p migration-reports
```

**File Location:** `migration-reports/phase-07-messaging-migration.json`

This report should be created in the target Quarkus project at `<quarkus_target_dir>/migration-reports/phase-07-messaging-migration.json`.

```json
{
  "phase": "messaging-migration",
  "status": "completed",
  "listeners_migrated": 5,
  "producers_migrated": 3,
  "messaging_type": "kafka",
  "channels_configured": 8,
  "files": [],
  "package_status": "PASS"
}
```

Update migration-spec.yaml transformations.messaging-migration section.

Then update `migration-metadata/migration-context.json`:
- Add `"7-messaging"` to `completedPhases`
- Set `phaseReports["7-messaging"]` to `"migration-reports/phase-07-messaging-migration.json"`
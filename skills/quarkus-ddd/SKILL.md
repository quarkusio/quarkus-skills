---
name: quarkus-ddd
description: "Scaffold and generate Domain-Driven Design components with Hexagonal Architecture in Quarkus projects. Use this skill whenever the user wants to create a new bounded context, add an aggregate, create value objects, scaffold a DDD module, add a new subdomain, or generate any DDD tactical pattern (aggregate, entity, value object, command, event, repository, service, endpoint) in a Quarkus/Java project. Also trigger when the user mentions 'hexagonal architecture', 'ports and adapters', or asks to add a new feature following DDD patterns."
---

# DDD + Hexagonal Architecture with Quarkus

Scaffold bounded contexts and DDD tactical patterns in Quarkus projects using Hexagonal Architecture (Ports and Adapters).

## Getting Started

When the user asks to create DDD components, first gather the essentials:

1. **What bounded context?** (e.g., `conference`)
2. **What subdomain?** (e.g., `sessions`, `attendees`, `speakers`)
3. **What is the aggregate root?** (e.g., `Session`, `Attendee`)
4. **What fields/properties does the aggregate have?**
5. **Are there value objects?** (e.g., `Address`, `TimeSlot`)
6. **What is the initial command?** (e.g., `RegisterAttendee`, `CreateSession`)
7. **What domain event should be emitted?** (e.g., `AttendeeRegistered`, `SessionCreated`)

Then ask:

> How would you like to proceed?
> 1. **Full scaffolding** — Generate all layers at once (domain, infrastructure, persistence)
> 2. **Layer by layer** — Walk through each layer step by step, discussing decisions along the way

Proceed based on their choice.

## Stack Defaults

Default to:
- **Persistence:** Hibernate ORM with Panache (`PanacheRepository`)
- **Messaging:** Kafka via MicroProfile Reactive Messaging (`@Channel`, `Emitter`)
- **REST:** Quarkus REST (Jakarta REST / JAX-RS)
- **Java version:** 25 (use records, sealed classes where appropriate)

If the user hasn't specified a Java version, ask which version they'd like to use and suggest 25 as the default.

If the user requests a different stack (e.g., Reactive Hibernate, RabbitMQ, Active Record pattern), adapt accordingly.

## Package Structure

Strict hierarchy — all bounded contexts follow this exact structure:

```
{basepackage}
└── {boundedcontext}
    └── {subdomain}
        ├── domain
        │   ├── aggregates      ← Aggregate root classes
        │   ├── events          ← Domain event records
        │   ├── services        ← Application service, commands, result records
        │   └── valueobjects    ← Value object records
        ├── infrastructure      ← REST endpoints, DTOs, event publishers
        └── persistence         ← JPA entities, Panache repositories
```

Example: for a `Session` aggregate in the `conference.sessions` subdomain:
```
conference.sessions.domain.aggregates.Session
conference.sessions.domain.events.SessionCreatedEvent
conference.sessions.domain.services.SessionService
conference.sessions.domain.services.CreateSessionCommand
conference.sessions.domain.services.SessionCreationResult
conference.sessions.domain.valueobjects.TimeSlot
conference.sessions.infrastructure.SessionEndpoint
conference.sessions.infrastructure.SessionDTO
conference.sessions.infrastructure.SessionEventPublisher
conference.sessions.persistence.SessionEntity
conference.sessions.persistence.SessionRepository
conference.sessions.persistence.TimeSlotEntity
```

## Class Naming

| Role | Pattern | Example |
|------|---------|--------|
| Aggregate | `{Noun}` | `Session` |
| Value Object | `{Noun}` | `TimeSlot` |
| JPA Entity | `{Noun}Entity` | `SessionEntity` |
| Repository | `{Noun}Repository` | `SessionRepository` |
| Service | `{Noun}Service` | `SessionService` |
| Endpoint | `{Noun}Endpoint` | `SessionEndpoint` |
| DTO | `{Noun}DTO` | `SessionDTO` |
| Command | `{Verb}{Noun}Command` | `CreateSessionCommand` |
| Domain Event | `{Noun}{PastTense}Event` | `SessionCreatedEvent` |
| Result | `{Noun}{Action}Result` | `SessionCreationResult` |
| Event Publisher | `{Noun}EventPublisher` | `SessionEventPublisher` |

## Generation Order

When doing full scaffolding, generate files in this order (domain first, adapters last):

### 1. Value Objects (`domain/valueobjects/`)

Java records with compact constructor validation.

```java
package {basepackage}.{boundedcontext}.{subdomain}.domain.valueobjects;

public record TimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
    public TimeSlot {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("End time cannot be null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
    }
}
```

- Throw `IllegalArgumentException` for required fields — invalid value objects must never exist
- Some fields may be nullable by design — skip validation for those
- No setters, no mutation — records enforce this

### 2. Domain Event (`domain/events/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.domain.events;

/**
 * "A Domain Event is a record of some business-significant occurrence in a Bounded Context."
 * Vaughn Vernon, Domain-Driven Design Distilled, 2016
 */
public record SessionCreatedEvent(String sessionId, String title) {
}
```

- Records with only the data other bounded contexts need
- Do NOT include the full aggregate state — keep events lean

### 3. Command (`domain/services/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.domain.services;

/**
 * "Commands (also known as modifiers) are operations that affect some change to the systems."
 * Eric Evans, Domain-Driven Design: Tackling Complexity in the Heart of Software, 2003.
 */
public record CreateSessionCommand(String title, String description, TimeSlot timeSlot) {
}
```

- The command IS the API contract — it is used directly as the REST request body
- Include value objects as fields where appropriate

### 4. Result Record (`domain/services/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.domain.services;

public record SessionCreationResult(Session session, SessionCreatedEvent sessionCreatedEvent) {
}
```

- Bundles the aggregate and its domain event(s)
- The service unpacks this to persist and publish separately

### 5. Aggregate (`domain/aggregates/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.domain.aggregates;

/**
 * "An AGGREGATE is a cluster of associated objects that we treat as a unit
 *  for the purpose of data changes."
 * Eric Evans, Domain-Driven Design: Tackling Complexity in the Heart of Software, 2003
 */
public class Session {

    String title;
    String description;
    TimeSlot timeSlot;

    protected Session(String title, String description, TimeSlot timeSlot) {
        this.title = title;
        this.description = description;
        this.timeSlot = timeSlot;
    }

    public static SessionCreationResult createSession(String title, String description, TimeSlot timeSlot) {
        Session session = new Session(title, description, timeSlot);
        SessionCreatedEvent event = new SessionCreatedEvent(session.getTitle(), session.getTitle());
        return new SessionCreationResult(session, event);
    }

    // Getters (public)
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TimeSlot getTimeSlot() { return timeSlot; }
}
```

Key rules:
- Constructor is `protected` — external code uses the static factory method
- The aggregate owns the decision of what domain events to emit — services do not create events
- Factory methods return a Result record, not the aggregate itself
- Fields are package-private, getters are public

### 6. DTO (`infrastructure/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.infrastructure;

/**
 * DTO (Data Transfer Object). DTOs are not specifically a DDD concept.
 */
public record SessionDTO(String title, String description) {
}
```

- Lives in `infrastructure/` because it serves external adapters
- Never use entity classes or aggregates as API responses

### 7. Event Publisher (`infrastructure/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.infrastructure;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * "The application has a semantically sound interaction with the adapters on all sides of it,
 *  without actually knowing the nature of the things on the other side of the adapters."
 * Alistair Cockburn, Hexagonal Architecture, 2005.
 */
@ApplicationScoped
public class SessionEventPublisher {

    @Channel("sessions")
    public Emitter<SessionCreatedEvent> sessionsTopic;

    public void publish(SessionCreatedEvent event) {
        sessionsTopic.send(event);
    }
}
```

- Outbound adapter — handles Kafka transport details
- Channel name matches the subdomain name (e.g., `sessions`)

### 8. Application Service (`domain/services/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.domain.services;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * "The application and domain layers call on the SERVICES provided by the infrastructure layer."
 * Eric Evans, Domain-Driven Design: Tackling Complexity in the Heart of Software, 2003.
 */
@ApplicationScoped
public class SessionService {

    @Inject
    SessionRepository sessionRepository;

    @Inject
    SessionEventPublisher sessionEventPublisher;

    public SessionDTO createSession(CreateSessionCommand command) {
        // Domain logic — aggregate factory creates the aggregate and event
        SessionCreationResult result = Session.createSession(
            command.title(),
            command.description(),
            command.timeSlot()
        );

        // Persist in a separate transaction
        QuarkusTransaction.requiringNew().run(() -> {
            sessionRepository.persist(result.session());
        });

        // Publish event AFTER persistence succeeds
        sessionEventPublisher.publish(result.sessionCreatedEvent());

        return new SessionDTO(result.session().getTitle(), result.session().getDescription());
    }
}
```

Critical ordering:
1. Call aggregate factory → get result (aggregate + event)
2. Persist aggregate in `QuarkusTransaction.requiringNew()` — isolated transaction
3. Publish event AFTER persistence — never inside the transaction
4. Return DTO

Use `@Inject` field injection. Use `@ApplicationScoped`.

### 9. Persistence Layer (`persistence/`)

**Entity for value objects:**

```java
package {basepackage}.{boundedcontext}.{subdomain}.persistence;

import jakarta.persistence.*;

@Entity
public class TimeSlotEntity {

    @Id @GeneratedValue
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    protected TimeSlotEntity() {}

    protected TimeSlotEntity(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Package-private getters and setters
}
```

**Aggregate entity:**

```java
package {basepackage}.{boundedcontext}.{subdomain}.persistence;

import jakarta.persistence.*;

/**
 * "An Entity models an individual thing. Each Entity has a unique identity."
 * Vaughn Vernon, Domain-Driven Design Distilled, 2016
 */
@Entity
public class SessionEntity {

    @Id @GeneratedValue
    private Long id;
    private String title;
    private String description;

    @OneToOne(cascade = CascadeType.ALL)
    TimeSlotEntity timeSlot;

    protected SessionEntity() {}

    SessionEntity(String title, String description, TimeSlotEntity timeSlot) {
        this.title = title;
        this.description = description;
        this.timeSlot = timeSlot;
    }

    // Package-private or protected getters
}
```

**Repository with aggregate-to-entity mapping:**

```java
package {basepackage}.{boundedcontext}.{subdomain}.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * "A REPOSITORY represents all objects of a certain type as a conceptual set."
 * Eric Evans, Domain-Driven Design: Tackling Complexity in the Heart of Software, 2003.
 */
@ApplicationScoped
public class SessionRepository implements PanacheRepository<SessionEntity> {

    public void persist(Session aggregate) {
        SessionEntity entity = fromAggregate(aggregate);
        persist(entity);
    }

    private SessionEntity fromAggregate(Session session) {
        TimeSlotEntity timeSlotEntity = new TimeSlotEntity(
            session.getTimeSlot().startTime(),
            session.getTimeSlot().endTime()
        );
        return new SessionEntity(session.getTitle(), session.getDescription(), timeSlotEntity);
    }
}
```

Key rules:
- Aggregates are plain Java classes — never annotate with JPA
- Entities are separate classes in `persistence/`
- Repository handles all aggregate ↔ entity mapping
- Entity constructors are package-private or `protected`
- Value objects map to separate entity classes with `@OneToOne(cascade = CascadeType.ALL)`

### 10. REST Endpoint (`infrastructure/`)

```java
package {basepackage}.{boundedcontext}.{subdomain}.infrastructure;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * "The application is blissfully ignorant of the nature of the input device."
 * Alistair Cockburn, Hexagonal Architecture, 2005.
 */
@Path("/{subdomain}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SessionEndpoint {

    @Inject
    SessionService sessionService;

    @POST
    public Response createSession(CreateSessionCommand command) {
        Log.debugf("Creating session %s", command);
        SessionDTO dto = sessionService.createSession(command);
        Log.debugf("Created session %s", dto);
        return Response.created(URI.create("/" + dto.title())).entity(dto).build();
    }
}
```

- Name endpoints `{Noun}Endpoint` — not Controller or Resource
- Apply `@Path`, `@Consumes`, `@Produces` at class level
- Inject the service, not the repository
- Accept command records directly as the request body
- POST returns `201 Created` with location URI and DTO body
- Log at DEBUG level with `Log.debugf()`

### 11. Kafka Configuration

Add to `application.properties`:

```properties
mp.messaging.outgoing.{subdomain}.connector=smallrye-kafka
mp.messaging.outgoing.{subdomain}.topic={subdomain}
```

## Layer Dependency Rules

These are inviolable:

- `domain/` → no framework imports (except CDI annotations for service wiring)
- `infrastructure/` → depends on `domain/`
- `persistence/` → depends on `domain/`
- Aggregates and value objects have **zero** framework dependencies
- The application service bridges layers — it lives in `domain/services/` and orchestrates domain logic, persistence, and event publishing

## Testing

When asked to generate tests, follow these patterns:

**Test method naming:** descriptive snake_case sentences.

```java
@Test
void creating_a_session_returns_result_with_event() { }
```

**Domain unit tests:** Pure JUnit 5 — no Quarkus container.

```java
class SessionTest {
    @Test
    void creating_a_session_returns_result_with_event() {
        TimeSlot timeSlot = new TimeSlot(LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        SessionCreationResult result = Session.createSession("DDD Talk", "About DDD", timeSlot);

        assertNotNull(result.session());
        assertEquals("DDD Talk", result.sessionCreatedEvent().title());
    }
}
```

**Integration tests:** `@QuarkusTest` with Dev Services.

**REST API tests:** REST Assured with `@QuarkusTest`.

= Lecture Concepts <lecture-concepts>

This chapter maps the architectural patterns and concepts covered in the EDPO lectures to their concrete implementations in CryptoFlow. Each section defines the concept and describes how the project applies it, referencing specific services, classes, and configuration where relevant.

== Event-Driven Architecture

Event-Driven Architecture (EDA) is a design paradigm in which the flow of the system is determined by events — significant changes in state that are published to interested consumers. Producers and consumers are decoupled: the producer does not know who will consume the event, and the consumer does not know who produced it.

CryptoFlow is built entirely on this principle. All inter-service communication flows through Apache Kafka topics. No service makes synchronous REST or RPC calls to another service for domain operations. The `market-data-service` publishes `CryptoPriceUpdatedEvent` records without any awareness of downstream consumers. The `portfolio-service`, `transaction-service`, and any future services subscribe independently. This architecture is codified in ADR-0001, which mandates Kafka as the sole inter-service event bus.

== Event Notification

The Event Notification pattern is the simplest form of event-driven communication: a service emits a notification that something happened, and consumers react independently. The event carries enough information to identify what occurred.

In CryptoFlow, the `market-data-service` implements this pattern. Each `CryptoPriceUpdatedEvent` carries an `eventId`, `symbol`, `price`, and `timestamp` — a self-contained notification of a price change. The producer has no knowledge of who consumes these events or what they do with them. New consumers can subscribe to the `crypto.price.raw` topic without any change to the producing service.

== Event-Carried State Transfer

Event-Carried State Transfer (ECST) extends event notification by including enough state in the event for consumers to maintain their own local copies of data, eliminating the need for synchronous queries back to the source.

The `portfolio-service` implements ECST through its `LocalPriceCache` — a `ConcurrentHashMap<String, BigDecimal>` updated by the `PriceEventConsumer` on every received `CryptoPriceUpdatedEvent`. When the REST API receives a request for portfolio valuation, `PortfolioService.calculateTotalValue()` reads prices directly from this local cache without calling the `market-data-service`. The service continues to serve queries even when the producer is offline, trading freshness for availability. This pattern is documented in ADR-0002.

== Apache Kafka as Event Bus

Apache Kafka is a distributed event streaming platform that provides durable, ordered, and partitioned message delivery. It decouples producers from consumers through topics, allowing independent scaling and evolution of each side.

CryptoFlow uses Kafka in KRaft mode (no ZooKeeper) as the backbone for all domain event communication. Key configuration decisions include:
- *Topic `crypto.price.raw`*: 3 partitions, 1-hour retention, producer `acks=all` for durability (ADR-0001).
- *Dead letter topic `crypto.price.raw.DLT`*: 1 partition, 7-day retention for poison pills.
- *Deterministic partition key strategy*: the trading symbol (e.g., `BTCUSDT`) is used as the message key with a custom `symbolIndex % numPartitions` mapping that guarantees per-symbol ordering (ADR-0004).
- *JSON serialization* with Jackson, type headers disabled (ADR-0003).
- *Consumer groups*: `portfolio-service-group`, `transaction-service-group`, and `user-service-compensation`, each with `auto.offset.reset=earliest` and manual offset management.

The Kafka experiments conducted in Exercise 1 (see @results) validate these configuration choices by demonstrating the consequences of weaker settings.

== BPMN and Process Orchestration with Camunda 8

Business Process Model and Notation (BPMN) is a graphical standard for specifying business processes. Camunda 8, built on the Zeebe workflow engine, executes BPMN models as distributed process instances. Services participate as stateless job workers connected via gRPC.

CryptoFlow uses two BPMN processes:

+ *`userOnboarding.bpmn`* (deployed by `onboarding-service`): Orchestrates user registration as a multi-step saga including credential collection, confirmation email dispatch, timeout handling, parallel user and portfolio creation, and compensation on failure. The process uses parallel gateways, event-based gateways, timer events, message correlation, and compensation boundary events.

+ *`placeOrder.bpmn`* (deployed by `transaction-service`): Manages order placement with a user task for order entry, a service task for order processing, and an event-based gateway that waits for either a price match message or a 1-minute timeout, followed by email notification.

The adoption of Camunda 8 is documented in ADR-0008.

== Saga Pattern

The Saga pattern decomposes a long-lived distributed transaction into a sequence of local transactions, each with a corresponding compensating action that undoes its effects if a later step fails. This avoids distributed locks while maintaining eventual consistency.

The user onboarding flow implements an orchestrated saga. The `userOnboarding.bpmn` process coordinates three workers across two services:
+ `prepareUserWorker` generates a user ID and confirmation link.
+ `userCreationWorker` creates the user record (local transaction in `user-service`).
+ `portfolioCreationWorker` creates the portfolio record (local transaction in `portfolio-service`).

If either creation step fails — indicated by boolean flags `isUserCreated` / `isPortfolioCreated` — the BPMN process routes to compensation handlers that delete the successfully created entity and publish cross-service compensation events via Kafka. This design is detailed in ADR-0010, ADR-0011, and ADR-0012.

== Orchestration vs. Choreography

In orchestration, a central coordinator directs participants through a defined sequence. In choreography, participants react to events independently without a central controller.

CryptoFlow uses both patterns deliberately:

- *Orchestration*: The `onboarding-service` orchestrates user registration through a Camunda BPMN process. A central process definition controls the flow, including parallel execution, timeouts, and compensation. This provides full visibility into process state via Camunda Operate and ensures deterministic failure handling.

- *Choreography*: The market data distribution is purely choreographed. The `market-data-service` publishes price events, and consumers (`portfolio-service`, `transaction-service`) react independently. There is no central coordinator — each consumer decides how to process the events.

The choice of orchestration for onboarding (ADR-0010) was driven by the complexity of the parallel saga with compensation. The simpler price distribution flow remains choreographed.

== Compensation and Rollback

Compensation is the mechanism by which a saga undoes the effects of a previously committed local transaction. Unlike database rollback, compensation is a forward action — it executes new operations that logically reverse earlier ones.

CryptoFlow implements compensation through two mechanisms:

+ *Camunda compensation boundary events*: The BPMN process attaches compensation handlers to the user and portfolio creation tasks. When a sibling task fails, the process throws a compensation event that triggers deletion of the successfully created entity.

+ *Kafka-based cross-service compensation* (ADR-0011): `UserCompensationRequestedEvent` and `PortfolioCompensationRequestedEvent` flow through dedicated Kafka topics (`crypto.user.compensation`, `crypto.portfolio.compensation`). Both services listen for compensation events targeting their domain. Deletions are idempotent (keyed by `userId`) and use synchronous `kafkaTemplate.send(...).get()` to ensure the compensation event is persisted before acknowledging.

The flag-driven completion pattern (ADR-0012) ensures that Zeebe job workers always complete successfully, communicating outcomes through boolean variables rather than throwing errors. This guarantees that the BPMN process always reaches its compensation branches.

== Bounded Contexts

Bounded Contexts, from Domain-Driven Design, define explicit boundaries within which a particular domain model applies. Each context has its own ubiquitous language, data model, and rules.

CryptoFlow identifies five bounded contexts:

+ *Market Data* — owned by `market-data-service`. Responsible for ingesting external price feeds and publishing normalized price events.
+ *Portfolio Management* — owned by `portfolio-service`. Manages portfolios, holdings, and price caching. Exclusive owner of the `portfolio` and `holding` database tables.
+ *Trading* — owned by `transaction-service`. Handles order placement and price matching.
+ *User & Identity* — owned by `user-service`. Manages user accounts, confirmation links, and credentials.
+ *Onboarding* — owned by `onboarding-service`. Orchestrates the cross-context registration saga without owning persistent data.

Each service owns its data exclusively. Cross-service references use opaque string identifiers (e.g., `userId` as a UUID), never foreign keys. The `shared-events` module defines the event contracts between contexts (ADR-0005).

== Hexagonal Architecture

Hexagonal Architecture (Ports and Adapters) separates the application core from external concerns by defining ports at the boundary and adapters that implement the technical details of communication with the outside world.

All five CryptoFlow services follow this pattern. The package structure consistently separates:
- `adapter/in/` — inbound adapters (Kafka listeners, REST controllers, Camunda job workers)
- `adapter/out/` — outbound adapters (Kafka producers, JPA repositories)
- `application/` — application services containing business logic
- `domain/` — domain objects with no framework dependencies

For example, the `portfolio-service` has `PriceEventConsumer` as an inbound Kafka adapter, `PortfolioController` as an inbound web adapter, and `PortfolioRepository` as an outbound persistence adapter. The `PortfolioService` application layer depends only on domain objects and port interfaces, not on Kafka or JPA directly.

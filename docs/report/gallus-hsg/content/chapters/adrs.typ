= Architectural Decision Records <adrs>

This chapter presents all architectural decision records (ADRs) produced during the project. ADRs follow the format proposed by Michael Nygard and document the context, decision, and consequences of each significant architectural choice.

== ADR-0001: Kafka as Sole Inter-Service Communication <adr-0001>

*Status:* Accepted (partially superseded by ADR-0008 for orchestration)

*Context:* CryptoFlow requires a communication mechanism between microservices that supports loose coupling, asynchronous processing, and durable event delivery. The market data service produces high-frequency price updates that multiple downstream services need to consume independently.

*Decision:* All inter-service domain events flow exclusively through Apache Kafka. No service makes synchronous REST or gRPC calls to another service for domain operations. Kafka runs in KRaft mode with auto topic creation disabled. The main price topic uses 3 partitions, 1-hour retention, and `acks=all` for durability. A dead letter topic (`crypto.price.raw.DLT`) with 7-day retention captures poison pills. Consumers use `auto.offset.reset=earliest` with manual offset management for at-least-once delivery.

*Consequences:* Services are fully decoupled at runtime — producers do not know their consumers. New consumers can be added without modifying existing services. The trade-off is eventual consistency and the operational overhead of managing a Kafka cluster. ADR-0008 later added Zeebe for orchestration state, while Kafka continues to carry domain events.

== ADR-0002: Event-Carried State Transfer for Price Data <adr-0002>

*Status:* Accepted

*Context:* The portfolio service needs current prices to calculate portfolio valuations. Querying the market data service synchronously on every request would create tight coupling and a single point of failure.

*Decision:* The portfolio service maintains a local in-memory price cache (`ConcurrentHashMap<String, BigDecimal>`) updated by a Kafka consumer. The `PriceEventConsumer` writes every received `CryptoPriceUpdatedEvent` into the `LocalPriceCache`. Portfolio valuation reads from this cache, never from the market data service.

*Consequences:* The portfolio service remains available even when the market data service is offline — it serves slightly stale prices. The trade-off is eventual consistency: during producer downtime or consumer lag, cached prices may not reflect the latest market state. A 503 response is returned until the first price event arrives (warm-up period).

== ADR-0003: JSON Serialization for Kafka Messages <adr-0003>

*Status:* Accepted

*Context:* Kafka messages require a serialization format. Options include JSON, Avro with Schema Registry, and Protobuf.

*Decision:* Use Jackson `JsonSerializer` / `JsonDeserializer` for all Kafka messages. Event schemas are defined as Java records in the `shared-events` module. Type headers are disabled (`spring.json.add.type.headers=false`).

*Consequences:* JSON is human-readable and requires no additional infrastructure. The trade-off is larger message sizes compared to binary formats and no broker-side schema validation. Schema evolution relies on compile-time enforcement through the shared module. A migration path to Avro exists if throughput becomes a bottleneck.

== ADR-0004: Trading Symbol as Kafka Partition Key <adr-0004>

*Status:* Accepted

*Context:* Kafka partitioning determines both parallelism and ordering guarantees. For price events, per-symbol ordering is critical to prevent stale prices from overwriting newer ones.

*Decision:* The trading symbol (e.g., `BTCUSDT`) is used as the Kafka message key. A deterministic `symbolIndex % numPartitions` mapping distributes symbols evenly across partitions. With 6 symbols and 3 partitions, each partition receives exactly 2 symbols.

*Consequences:* All events for a given symbol land in the same partition, guaranteeing per-symbol ordering. The deterministic mapping ensures even distribution regardless of hash properties. The `eventId` (UUID) serves as the unique event identifier, while the message key is purely a routing key.

== ADR-0005: Monorepo with Shared Events Module <adr-0005>

*Status:* Accepted

*Context:* Multiple services consume the same event types. Schema drift between producers and consumers could cause runtime deserialization failures.

*Decision:* All services live in a single Maven multi-module repository. Event records are defined once in the `shared-events` module and referenced as a compile-time dependency by all services.

*Consequences:* Schema changes are atomic — a single commit updates the event definition and all affected services. The trade-off is tight build-time coupling: any change to `shared-events` triggers a rebuild of all dependent services. For a two-person team this is acceptable; at larger scale, a schema registry approach might be preferred.

== ADR-0006: Binance WebSocket Streams for Market Data <adr-0006>

*Status:* Accepted

*Context:* The market data service needs a real-time source of cryptocurrency prices. Options include REST polling, WebSocket streaming, and third-party aggregators.

*Decision:* Subscribe to Binance public WebSocket streams (`wss://stream.binance.com:9443/ws/<symbol>\@ticker`) for 24-hour ticker data. No API key is required for public streams.

*Consequences:* The system receives sub-second push updates with zero cost and no rate-limit management. Automatic reconnection with exponential backoff handles transient disconnections. The trade-off is dependency on a single external provider and variable event rates determined by market activity.

== ADR-0007: PostgreSQL with Flyway for Portfolio Persistence <adr-0007>

*Status:* Accepted

*Context:* The portfolio service needs durable storage for portfolios and holdings. The database schema must be version-controlled and reproducible across environments.

*Decision:* Use PostgreSQL 16 as the persistence store. Flyway manages schema migrations with versioned SQL scripts. Hibernate runs in `validate` mode only. The portfolio service is the exclusive owner of its tables; no other service reads from or writes to them. Cross-service references use opaque string identifiers, not foreign keys.

*Consequences:* Schema evolution is explicit, auditable, and reproducible. Validate-only mode prevents accidental schema changes at startup. Two tables are defined: `portfolio` (one row per user, `user_id UNIQUE`) and `holding` (one row per symbol per portfolio, `ON DELETE CASCADE`).

== ADR-0008: Camunda 8 (Zeebe) for Process Orchestration <adr-0008>

*Status:* Accepted (partially supersedes ADR-0001)

*Context:* Order placement and user onboarding require multi-step workflows spanning multiple services, with timeout handling, conditional branching, and compensation. Pure Kafka choreography would make these flows difficult to reason about and monitor.

*Decision:* Adopt Camunda 8 with the Zeebe engine for BPMN process orchestration. Zeebe operates as a managed SaaS platform. Services deploy BPMN models via the `@Deployment` annotation and participate as stateless gRPC job workers. Kafka continues to carry domain events; Zeebe handles orchestration state.

*Consequences:* Business processes are visually modeled in BPMN and executable. Camunda Operate provides runtime visibility into process instances. The trade-off is dependency on an external SaaS platform and the learning curve of BPMN modeling. The clear separation — Kafka for events, Zeebe for orchestration — keeps each technology in its strength area.

== ADR-0009: User Service with Orchestrated Onboarding <adr-0009>

*Status:* Accepted

*Context:* User registration requires multiple steps: credential collection, email confirmation, and persistent storage. The confirmation step introduces a waiting period that does not fit a synchronous request/response model.

*Decision:* The user service owns the `User` aggregate. Registration is modeled as a Camunda BPMN process: collect credentials, send a confirmation email, wait for the user to click the link (message correlation), then persist the user record. No intermediate registration state is stored in the database — pending registrations exist only as Zeebe process instances.

*Consequences:* The process state is managed by Zeebe, eliminating the need for a custom state machine in application code. Message correlation handles the asynchronous confirmation cleanly. Pending registrations are not queryable from the user service's database — only confirmed users are persisted.

== ADR-0010: Dedicated Onboarding Service as Saga Orchestrator <adr-0010>

*Status:* Accepted

*Context:* Registration evolved to require both user creation and portfolio creation — a distributed transaction across two bounded contexts. Placing the orchestration logic in either participating service would create undesirable coupling.

*Decision:* Introduce a dedicated `onboarding-service` that owns the `userOnboarding.bpmn` process and acts as the saga orchestrator. The process coordinates workers across two services: `prepareUserWorker` and `userCreationWorker` in user-service, `portfolioCreationWorker` in portfolio-service. User and portfolio creation execute in parallel via a BPMN parallel gateway. A boundary timer invalidates unconfirmed links after 1 minute.

*Consequences:* The orchestration concern is isolated in its own service, keeping user-service and portfolio-service focused on their domains. The parallel saga pattern enables concurrent creation across contexts. The trade-off is an additional service to deploy, though it is lightweight (no persistent storage).

== ADR-0011: Event-Carried Compensation for Onboarding Failures <adr-0011>

*Status:* Accepted

*Context:* In the onboarding saga, either user or portfolio creation can fail after the sibling has succeeded, leaving an orphaned entity. Compensation must work across service boundaries without synchronous coupling.

*Decision:* Cross-service compensation flows through Kafka events. When user creation fails after portfolio creation succeeded, the compensation worker deletes the user and publishes a `PortfolioCompensationRequestedEvent`. When portfolio creation fails after user creation succeeded, the compensation worker deletes the portfolio and publishes a `UserCompensationRequestedEvent`. Both services also listen to the opposite compensation topic for defense in depth. Producers use synchronous `kafkaTemplate.send(...).get()` to guarantee persistence before acknowledging.

*Consequences:* Compensation is loosely coupled — no direct HTTP calls during failure handling. Deletions are idempotent (keyed by `userId`), making at-least-once delivery safe. The dual-listener pattern provides a safety net even if the orchestrator crashes. The trade-off is increased complexity in the event flow.

== ADR-0012: Flag-Driven Completion for Creation Tasks <adr-0012>

*Status:* Accepted

*Context:* Zeebe error handling (throwing `BpmnError`) would short-circuit the BPMN flow, potentially bypassing compensation branches. If a worker throws an error, the process might terminate before reaching the gateway that routes to compensation.

*Decision:* Both `userCreationWorker` and `portfolioCreationWorker` always complete their jobs successfully, regardless of the actual outcome. They communicate results exclusively through boolean process variables (`isUserCreated`, `isPortfolioCreated`). BPMN exclusive gateways inspect these flags: `true` routes to success, `false` routes to compensation.

*Consequences:* The BPMN process always reaches its decision gateways, ensuring compensation logic is reachable for all failure modes — duplicates, validation errors, and transient connectivity issues alike. No Zeebe incidents need manual resolution. The trade-off is that errors are less visible in Camunda Operate (jobs appear successful), requiring operators to check service logs and compensation event trails.

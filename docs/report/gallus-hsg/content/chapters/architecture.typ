= Architecture <architecture>

This chapter describes the system architecture of CryptoFlow, covering the service topology, Kafka event flows, BPMN process models, and pointers to the architectural decision records that motivated the design.

== System Overview

CryptoFlow is a distributed system comprising five Spring Boot microservices that communicate exclusively through Apache Kafka events and Camunda 8 (Zeebe) process orchestration. No service makes synchronous REST or gRPC calls to another service for domain operations — REST endpoints are exposed only for external client access.

// TODO: Add system-overview.png to figures/ directory and uncomment the following:
// #figure(
//   image("../../figures/system-overview.png"),
//   caption: [High-level system overview showing the five services, Kafka topics, and Camunda 8 SaaS],
// ) <fig:system-overview>

The services and their roles are:

/ Market Data Service (8081): Inbound adapter to the Binance WebSocket API. Subscribes to real-time ticker streams for six cryptocurrency pairs and publishes `CryptoPriceUpdatedEvent` records to Kafka. Stateless — no database.

/ Portfolio Service (8082): Consumes price events into a local in-memory cache, persists portfolio and holding data in PostgreSQL, and exposes REST endpoints for valuation queries. Also acts as a Camunda job worker for portfolio creation during onboarding.

/ Transaction Service (8083): Deploys the `placeOrder.bpmn` process, matches pending orders against live prices, and correlates execution results back to the BPMN process. Consumes price events from Kafka for real-time matching.

/ User Service (8084): Manages user accounts, email confirmation links, and user lifecycle. Acts as a Camunda job worker for user preparation, creation, and invalidation during onboarding. Handles compensation by deleting users and publishing cross-service events.

/ Onboarding Service (8085): Saga orchestrator that deploys `userOnboarding.bpmn` to Zeebe. Owns no persistent data — all process state lives in the Zeebe engine.

Supporting infrastructure includes a Kafka broker (KRaft mode), PostgreSQL 16, Kafka UI for monitoring, and pgAdmin for database administration, all provisioned via Docker Compose.

== Kafka Topology

All domain events flow through Kafka. The following table summarizes the topic configuration:

#figure(
  caption: "Kafka topic configuration",
  table(
    columns: (auto, auto, auto, auto, 1fr),
    [*Topic*], [*Partitions*], [*Retention*], [*Key*], [*Purpose*],
    [`crypto.price.raw`], [3], [1 hour], [Symbol], [Live price ticks from Binance],
    [`crypto.price.raw.DLT`], [1], [7 days], [Original key], [Dead letter topic for poison pills],
    [`crypto.portfolio.compensation`], [--], [--], [`userId`], [Portfolio compensation requests],
    [`crypto.user.compensation`], [--], [--], [`userId`], [User compensation requests],
  ),
) <tab:kafka-topics>

The producer/consumer relationships are:

#figure(
  caption: "Kafka producers and consumers by service",
  table(
    columns: (auto, 1fr, 1fr),
    [*Service*], [*Produces to*], [*Consumes from*],
    [Market Data], [`crypto.price.raw`], [--],
    [Portfolio], [`crypto.user.compensation`], [`crypto.price.raw`, `crypto.portfolio.compensation`],
    [Transaction], [--], [`crypto.price.raw`],
    [User], [`crypto.portfolio.compensation`], [`crypto.user.compensation`],
    [Onboarding], [--], [--],
  ),
) <tab:kafka-producers-consumers>

The partition key strategy uses the trading symbol (e.g., `BTCUSDT`) for price events, ensuring all events for a given symbol land in the same partition and are processed in order. A deterministic `symbolIndex % numPartitions` mapping distributes six symbols evenly across three partitions (ADR-0004). Compensation events use the `userId` (UUID) as the partition key.

== BPMN Process Flows

=== User Onboarding Process

The `userOnboarding.bpmn` process, deployed by the onboarding service, orchestrates user registration as a parallel saga:

+ A user task collects credentials (username, password, email).
+ The `prepareUserWorker` generates a UUID `userId`, creates a confirmation link (status: PENDING), and builds the confirmation email content.
+ A Camunda email connector sends the confirmation email via SMTP.
+ An event-based gateway waits for either:
  - A `Message_UserConfirmed` message, correlated by `userConfirmationLinkId`, indicating the user clicked the confirmation link; or
  - A 1-minute timer expiration, after which the `invalidateConfirmationWorker` marks the link as INVALIDATED and the process ends.
+ Upon confirmation, a parallel gateway splits execution:
  - `userCreationWorker` creates the user record in PostgreSQL.
  - `portfolioCreationWorker` creates a portfolio record in PostgreSQL.
+ Both workers always complete successfully, communicating outcomes through boolean flags (`isUserCreated`, `isPortfolioCreated`).
+ Exclusive gateways inspect the flags. If either is `false`, compensation events trigger deletion of the sibling entity.
+ If both are `true`, the parallel paths join and the process completes successfully.

// TODO: Add userOnboarding-bpmn.png to figures/ directory and uncomment:
// #figure(
//   image("../../figures/userOnboarding-bpmn.png"),
//   caption: [User onboarding BPMN process with parallel saga and compensation],
// ) <fig:onboarding-bpmn>

=== Place Order Process

The `placeOrder.bpmn` process, deployed by the transaction service, handles order execution:

+ A user task collects order details (symbol, amount, target price).
+ The `order-processing-worker` creates a pending order and begins matching against live prices.
+ An event-based gateway waits for either:
  - A `price-matched` message, correlated by `transactionId`, indicating a market price matched the order; or
  - A 1-minute timeout.
+ The outcome (execution or rejection) triggers an email notification to the user.

// TODO: Add placeOrder-bpmn.png to figures/ directory and uncomment:
// #figure(
//   image("../../figures/placeOrder-bpmn.png"),
//   caption: [Place order BPMN process with price matching and timeout],
// ) <fig:placeorder-bpmn>

== Key Design Decisions

The architecture is supported by twelve formal Architectural Decision Records (ADRs), documented in full in @adrs. The most significant decisions include:

- *ADR-0001*: Kafka as the sole inter-service communication channel — no synchronous REST between services.
- *ADR-0002*: Event-Carried State Transfer for price data, enabling the portfolio service to answer queries without calling the market data service.
- *ADR-0008*: Camunda 8 (Zeebe) for process orchestration, chosen for its partitioned log, stateless worker model, and managed SaaS deployment.
- *ADR-0010*: A dedicated onboarding service as saga orchestrator, separating the cross-context coordination concern from the participating services.
- *ADR-0011*: Event-carried compensation via Kafka for cross-service rollback, maintaining loose coupling even during failure handling.
- *ADR-0012*: Flag-driven completion for Camunda workers, ensuring BPMN compensation branches are always reachable.

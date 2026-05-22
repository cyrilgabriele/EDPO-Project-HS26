= Architectural Decision Records <adrs>

This chapter summarises the current architectural decision records (ADRs) referenced in the report. The authoritative ADR sources are maintained in `docs/adrs`; the summaries below condense their status, context, decision, and architectural impact.

== ADR-0001: Kafka as Inter-Service Event Bus <adr-0001>

*Status:* Partially superseded by ADR-0008; Kafka remains the event bus for domain events.

*Context:* CryptoFlow services must exchange price updates and business events without direct runtime coupling. REST would make services block on each other, while RabbitMQ would not provide Kafka's replayable log and partitioned stream model.

*Decision:* All inter-service domain events flow through Apache Kafka, while REST endpoints remain reserved for external clients. The platform uses a single-node KRaft broker, explicitly declared topics, manual offset handling, a dead-letter topic for poison pills, and `acks=all` for durable writes.

*Consequences:* Services are loosely and temporally coupled, and failed records can be isolated without stalling whole consumers. The trade-off is eventual consistency, Kafka operations, and a short retention window that prevents Kafka from acting as the long-term system of record.

== ADR-0002: Event-Carried State Transfer for Price Replication <adr-0002>

*Status:* Accepted

*Context:* `portfolio-service` needs current prices to value holdings. Calling `market-data-service` synchronously on each request would add latency and create an availability dependency.

*Decision:* `portfolio-service` consumes `CryptoPriceUpdatedEvent` messages and maintains an in-memory price cache using Event-Carried State Transfer. Portfolio valuation always reads from this local cache.

*Consequences:* Valuations remain fast and available even if market-data publishing is temporarily interrupted. The trade-off is slightly stale data during lag or reconnection windows and a short warm-up phase after cold start.

== ADR-0003: JSON Serialization for Kafka Messages <adr-0003>

*Status:* Accepted

*Context:* Kafka messages require a shared serialization format. JSON, Avro, and Protocol Buffers differ in readability, schema enforcement, and operational overhead.

*Decision:* CryptoFlow standardises on Jackson JSON serialization and deserialization, with event schemas defined as Java records in the `shared-events` module and type headers disabled.

*Consequences:* Messages are easy to inspect and no schema registry is required. The trade-off is larger payloads and the absence of broker-side schema validation or automated compatibility guarantees.

== ADR-0004: Trading Symbol as Kafka Partition Key <adr-0004>

*Status:* Accepted

*Context:* Price events must preserve per-symbol ordering so that newer prices cannot be overwritten by older ones. A poor keying strategy would distribute updates for one symbol across multiple partitions.

*Decision:* The Kafka message key is the trading symbol, such as `BTCUSDT`. Partition assignment uses a deterministic symbol-index mapping before falling back to hash-based routing for unknown symbols.

*Consequences:* All updates for one symbol stay ordered within a single partition while load remains evenly distributed for the fixed symbol set. The message key becomes a routing key only; uniqueness remains the responsibility of the event ID.

== ADR-0005: Shared Library Module for Event Schemas <adr-0005>

*Status:* Accepted

*Context:* Producers and consumers must agree on event schemas. Duplicating DTOs in every service would invite schema drift and runtime deserialization failures.

*Decision:* Event schemas live in a dedicated `shared-events` Maven module that is used as a compile-time dependency by every producing and consuming service. The module contains schema records and serialization dependencies only.

*Consequences:* Schema changes become atomic and type-safe across the codebase. The cost is build-time coupling, because services depending on `shared-events` must be rebuilt when the shared contract changes.

== ADR-0006: Binance WebSocket Streams for Market Data <adr-0006>

*Status:* Accepted

*Context:* The platform needs a live market-data source. Polling a REST API would introduce artificial delay, while paid providers would add cost and operational overhead.

*Decision:* `market-data-service` subscribes to Binance public WebSocket streams at `wss://stream.binance.com:9443/ws/<symbol>\@ticker` and forwards price updates into Kafka.

*Consequences:* The system receives sub-second push updates at zero cost and stays event-driven end-to-end. The trade-off is dependence on one upstream provider and the need to manage reconnects, heartbeats, and variable event rates.

== ADR-0007: Portfolio-Service as Sole Data Owner of the Portfolio Bounded Context <adr-0007>

*Status:* Accepted

*Context:* Portfolio holdings are stateful domain data with their own invariants. Sharing database access across services would blur ownership and make schema evolution risky.

*Decision:* `portfolio-service` is the exclusive owner of portfolio persistence. It uses PostgreSQL 16, manages schema changes through Flyway, keeps Hibernate in `validate` mode, and accepts cross-service interaction only through events and opaque identifiers.

*Consequences:* Portfolio schema evolution becomes explicit, auditable, and local to one service. The trade-off is that other services cannot join against portfolio tables and must instead rely on asynchronous integration patterns.

== ADR-0008: Camunda 8 (Zeebe) as the Process Orchestration Engine <adr-0008>

*Status:* Accepted; partially supersedes ADR-0001 for orchestrated workflows.

*Context:* CryptoFlow already implements two concrete orchestrated workflows, `userOnboarding` and `placeOrder`. Both BPMN models use Camunda 8's out-of-the-box outbound email connector for notifications, and a production-grade version of the system would need to scale many concurrent workflow instances in a cloud deployment. Pure Kafka choreography would not provide a central process-state authority or sufficient operational visibility.

*Decision:* CryptoFlow standardises on Camunda 8 with Zeebe for process orchestration. BPMN models are deployed from the services, workers run as stateless gRPC clients, and Kafka continues to carry domain events while Zeebe manages orchestration state and correlations. Kafka and email integration stay inside the BPMN models through Camunda connector templates rather than custom application-level client code.

*Consequences:* The architecture gains executable BPMN models, scalable parallel workflow execution through Zeebe's cloud-oriented design, connector-based notification steps, and strong runtime visibility through Camunda Operate. The trade-off is dependence on an external SaaS engine and a Zeebe-specific learning curve.

== ADR-0009: User-Service as Owner of User Identity and Confirmation State <adr-0009>

*Status:* Accepted; amended by ADR-0010.

*Context:* Several services depend on a verified user identity, but the system needed one service to own user lifecycle and email confirmation semantics. After ADR-0010 moved orchestration into `onboarding-service`, that ownership question still remained.

*Decision:* `user-service` becomes the authoritative owner of the `User` aggregate and confirmation lifecycle. It prepares and persists pending confirmation links, exposes the public confirmation endpoint, correlates `UserConfirmed` back into Camunda, publishes `user.confirmed`, and persists the user record only after confirmation.

*Consequences:* Identity ownership and confirmation rules are centralised in one bounded context, and every persisted user is guaranteed to be confirmed. The trade-off is additional persistence and operational surface inside `user-service`, plus the need for an externally reachable confirmation URL.

== ADR-0010: Dedicated Onboarding-Service as Saga Orchestrator <adr-0010>

*Status:* Accepted

*Context:* Registration evolved into a distributed workflow: after confirmation, both user creation and portfolio creation must complete across separate bounded contexts. Keeping that saga inside `user-service` would create unnecessary coupling.

*Decision:* A dedicated `onboarding-service` owns `userOnboarding.bpmn` and orchestrates the onboarding saga. It coordinates `prepareUserWorker`, waits for the user-confirmed message, fans out to `userCreationWorker` and `portfolioCreationWorker` in parallel, and uses a boundary timer to invalidate stale confirmation links.

*Consequences:* Orchestration is isolated from domain ownership, and the onboarding flow now models the Parallel Saga pattern explicitly. The trade-off is an additional deployable unit with its own Camunda credentials, deployment lifecycle, and monitoring needs.

== ADR-0011: Event-Carried Compensation for Onboarding Failures <adr-0011>

*Status:* Accepted

*Context:* In the onboarding saga, either local creation step can fail after the sibling service already succeeded. Compensation must therefore cross service boundaries without introducing synchronous coupling.

*Decision:* Compensation is carried through Kafka events. The service that detects failure deletes its own state and publishes a compensation request so the sibling service can clean up; both sides also listen defensively to the opposite compensation topic.

*Consequences:* Compensation remains loosely coupled and safe under at-least-once delivery because deletions are idempotent. The trade-off is a more complex failure flow and additional event choreography to understand and monitor.

== ADR-0012: Flag-Driven Completion for Creation Tasks <adr-0012>

*Status:* Accepted

*Context:* Throwing BPMN errors directly from creation workers could terminate the onboarding process before compensation branches become reachable. The saga needed a way to represent failure without short-circuiting the BPMN control flow.

*Decision:* `userCreationWorker` and `portfolioCreationWorker` always complete their Zeebe jobs and communicate outcomes through boolean process variables such as `isUserCreated` and `isPortfolioCreated`. Exclusive gateways then route toward success or compensation.

*Consequences:* Compensation paths remain reachable for every failure mode, including validation and duplicate cases. The trade-off is reduced failure visibility in Camunda Operate because technically the jobs complete successfully.

== ADR-0013: Fairy Tale Saga for the `placeOrder` Workflow <adr-0013>

*Status:* Accepted

*Context:* The `placeOrder` process contains an Event-Based Gateway that may wait for a price match for an arbitrary amount of time. Cross-service consistency with portfolio updates is required, but local ACID transactions must remain service-local.

*Decision:* The workflow adopts the Fairy Tale Saga pattern: synchronous Zeebe service tasks for local transactions and eventual cross-service consistency through Kafka. Deterministic business failures are handled via BPMN error boundaries rather than saga-wide compensation.

*Consequences:* Zeebe can suspend the workflow at the price-match wait without holding open database or network resources, and each service keeps its own transaction boundary. The trade-off is an accepted eventual-consistency window after order approval and no compensation once an order is semantically terminal.

== ADR-0014: Outbox Pattern for Order Approval <adr-0014>

*Status:* Accepted

*Context:* Approving an order requires both persisting `APPROVED` in the transaction database and publishing `OrderApprovedEvent` to Kafka. A crash between those two steps would otherwise create silent data loss.

*Decision:* `transaction-service` uses the Transactional Outbox pattern. The approval step writes the status change and outbox payload in one local transaction, a publication step sends the event to Kafka, and a scheduled safety net republishes stale unpublished rows after crashes.

*Consequences:* Approval state and event publication become atomically durable, and broker outages no longer create unrecoverable gaps. The trade-off is an extra table, a background scheduler, and the need for downstream consumers to tolerate at-least-once delivery.

== ADR-0015: Portfolio Update Durability for Approved Orders <adr-0015>

*Status:* Accepted

*Context:* After the outbox event is published, the workflow must decide whether to wait for a portfolio acknowledgement before sending the executed email. Waiting would tighten coupling between `transaction-service` and `portfolio-service`.

*Decision:* The executed email is sent immediately after `publishOrderApprovedWorker` completes. Portfolio propagation remains pure Event-Carried State Transfer: `portfolio-service` consumes `OrderApprovedEvent` autonomously and no acknowledgement flows back into the BPMN process.

*Consequences:* Approval workflows finish promptly, email latency stays deterministic, and `portfolio-service` owns its own retry and DLT logic. The trade-off is that portfolio failures are no longer visible in Camunda Operate and users may briefly see approved orders before holdings catch up.

== ADR-0016: Idempotent Consumer for Portfolio Updates <adr-0016>

*Status:* Accepted

*Context:* Kafka delivers messages at least once. If `portfolio-service` crashes after updating holdings but before committing the consumer offset, replaying the same `OrderApprovedEvent` would otherwise double the holding quantity.

*Decision:* `portfolio-service` inserts the `transactionId` into a `processed_transaction` table with a `UNIQUE` constraint inside the same transaction as the holding update. Duplicate inserts fail fast and cause the event to be skipped safely.

*Consequences:* Re-delivered approval events no longer corrupt holdings, and the processed table doubles as an audit log. The trade-off is one extra database write per update and unbounded table growth unless retention is added later.

== ADR-0017: Replicated Read-Model for User Validation at Order Placement <adr-0017>

*Status:* Accepted

*Context:* `transaction-service` must reject orders from users who have not completed registration. Synchronous validation against `user-service` would block order placement on another service's availability.

*Decision:* `transaction-service` maintains its own confirmed-users read model in a local database table. `user-service` publishes `UserConfirmedEvent` to a log-compacted Kafka topic, and `transaction-service` consumes the event and upserts the local projection.

*Consequences:* User validation becomes a local database read and remains available even if `user-service` is down. The trade-off is a small eventual-consistency window and the future need for compensating events if users can later be deactivated.

== ADR-0018: Human Escalation for Deterministic Workflow Failures <adr-0018>

*Status:* Accepted

*Context:* Some approval-flow failures are deterministic, such as a missing transaction record or a missing outbox row. Retrying these cases indefinitely would not fix the root cause and would only delay operator visibility.

*Decision:* Workers throw typed BPMN errors for these failures, and interrupting boundary events route the process into an operations user task in Camunda Tasklist. The task carries the relevant order context and requires a resolution note before closure.

*Consequences:* Deterministic integrity problems surface immediately in Tasklist with an audit trail, and the process instance stays open until acknowledged. The trade-off is additional operational monitoring and no automatic retry path from the human-escalation step.

== ADR-0019: Database per Service for Stateful Bounded Contexts <adr-0019>

*Status:* Accepted

*Context:* CryptoFlow now contains several stateful bounded contexts: user identity, portfolio management, and trading. Sharing one database across them would couple schema evolution, transactions, deployments, and failure domains.

*Decision:* Every stateful bounded context gets its own database: `user-service` owns `user_service_db`, `portfolio-service` owns `portfolio_service_db`, and `transaction-service` owns `transaction_service_db`. Services must not read or write each other's tables; cross-service consistency is handled through events, replicated read models, outbox publication, and sagas.

*Consequences:* Structural coupling is reduced and each stateful service becomes a more independent architecture quantum. The trade-off is that cross-service joins and ACID transactions disappear, which makes eventual consistency and the supporting integration patterns a permanent architectural requirement.

== ADR-0020: Transaction-Service as Sole Owner of the Trading Bounded Context <adr-0020>

*Status:* Accepted

*Context:* Order placement, pending-order matching, approval or rejection, and reliable publication of approved trades all belong to one coherent trading domain. That domain needed a single service boundary rather than being split across portfolio, user, and onboarding concerns.

*Decision:* `transaction-service` is the sole owner of the trading bounded context. It owns `placeOrder.bpmn`, the Camunda workers, transaction lifecycle state, pending-order matching, publication of approved-order events, and local persistence such as `transaction_record`, `outbox_events`, and the confirmed-user validation read model.

*Consequences:* Trading invariants and behaviour are concentrated in one service boundary, while `portfolio-service` stays a downstream projection owner and `user-service` remains the identity owner. The trade-off is another deployable service with its own persistence model, but the responsibilities stay aligned with the domain.

== ADR-0021: Binance Partial Book Depth Streams for Market Scout <adr-0021>

*Status:* Accepted

*Context:* Market Scout needs continuous futures order-book input to derive ask-side signals and demonstrate filtering, translation, Avro contracts, and windowed processing. Binance offers request-response depth calls, partial book streams, and diff book streams.

*Decision:* The MVP uses Binance USD-M Futures Partial Book Depth Streams. They provide bounded top-N bid and ask levels with exchange timestamps, which fits the intended topology without requiring full local order-book reconstruction.

*Consequences:* Market Scout can miss ask opportunities outside the configured top-N depth, but the stream-processing topology stays simple and bounded. A future full-order-book implementation would need snapshot reconciliation, diff streams, sequence validation, and gap recovery.

== ADR-0022: Avro Contracts for Derived Market Scout Events <adr-0022>

*Status:* Accepted

*Context:* ADR-0003 standardised Kafka messages on JSON, but the Market Scout topology needs explicit contracts for translated and aggregated project-owned events while keeping raw exchange payloads replayable.

*Decision:* `crypto.scout.raw` remains a JSON replay boundary. Derived Market Scout events use Avro contracts for `AskQuote`, `AskOpportunity`, and `ScoutWindowSummary`, generated locally with registryless serdes.

*Consequences:* Raw ingestion remains inspectable and replayable, while derived topology outputs gain stronger contracts. The trade-off is a deliberate exception to the JSON default and no central schema compatibility enforcement until Schema Registry is introduced for broader cross-service derived contracts.

== ADR-0023: Split Partial Book Ingestion from Market Order Scout Processing <adr-0023>

*Status:* Accepted

*Context:* The initial Market Scout service both consumed Binance partial book streams and ran the Kafka Streams topology over `crypto.scout.raw`. That made the MVP simple, but weakened the service-boundary story around Kafka.

*Decision:* Split the flow into `market-partial-book-ingestion-service`, which owns Binance connectivity and raw JSON publication, and `market-order-scout-service`, which owns ask-side filtering, translation, thresholding, and windowed summaries.

*Consequences:* The architecture now has a clearer exchange-facing ingestion boundary and a separate domain-specific processing boundary. The trade-off is an additional Spring Boot module, Docker Compose entry, build artifact, and test surface.

== ADR-0024: New Kafka Streams Application ID for Market Order Scout <adr-0024>

*Status:* Accepted

*Context:* After ADR-0023, the Spring application name changed, which also changes the derived Kafka Streams `application.id`. Keeping the old ID would preserve offsets and state but retain coupling to the pre-split service identity.

*Decision:* Accept `market-order-scout-service-topology` as the new Kafka Streams application ID and do not migrate the old local state or offsets. The service may reprocess `crypto.scout.raw` from `earliest` when no committed offsets exist.

*Consequences:* The split gets a clean operational identity and uses the raw topic as the intended recovery path. The trade-off is possible duplicate derived records on reused local Kafka clusters, which is acceptable for the course project and local development context.

== ADR-0025: Derived Market Features for Market Scout Events <adr-0025>

*Status:* Accepted

*Context:* Partial-depth raw events already contain enough ask-side information to derive useful liquidity features, but those features belong to project-owned derived contracts rather than the exchange-facing raw contract.

*Decision:* Keep `crypto.scout.raw` unchanged and extend `AskQuote` and `AskOpportunity` with computed ask-side fields: `bestAskPrice`, `bestAskQuantity`, and per-level `askNotional`. `ScoutWindowSummary` remains unchanged.

*Consequences:* Derived scout events become more useful for ask liquidity analysis while the raw Binance capture stays stable. The trade-off is repeated best-ask context on each flattened quote and required Avro/test updates.

== ADR-0026: Matchable Ask as Cross-Service Ask Contract <adr-0026>

*Status:* Accepted

*Context:* `transaction-service` needs ask-side liquidity for matching, but should not depend on scout-local analysis events optimised for Market Scout concerns.

*Decision:* Introduce `MatchableAsk` in `shared-events` as the cross-service Avro contract. `market-order-scout-service` publishes it to `crypto.scout.matchable-asks`, keyed by normalised symbol, with deterministic `askQuoteId`, price, quantity, event time, and source venue.

*Consequences:* Trading can match against a narrow, stable contract while scout-local events remain free to evolve for analysis. The trade-off is one additional topic and Avro generation support in `shared-events`.

== ADR-0027: Event-Time Bid/Ask Matching Window <adr-0027>

*Status:* Accepted

*Context:* The previous order-placement implementation approved orders from ticker prices cached in memory, mixing valuation prices with executable liquidity and losing pending state on restart.

*Decision:* `transaction-service` owns a Kafka Streams topology over `transaction.buy-bids` and `crypto.scout.matchable-asks`, both keyed by symbol. A bid matches only if ask event time falls within the bid's 30-second validity window, price and quantity constraints pass, and price-time priority selects it.

*Consequences:* Matching now uses executable ask liquidity and event-time semantics instead of local heap state. Losing bids remain pending within their validity window, and Camunda's rejection timer is set to `PT35S` to include a bounded late/fallback margin.

== ADR-0028: Display Currency as User Identity Data <adr-0028>

*Status:* Accepted

*Context:* Users need portfolio values and buy-time quotes in a chosen currency, while user-service is the source of truth for user identity and preferences. Extending one-time confirmation events or calling user-service synchronously would blur event semantics or violate ECST.

*Decision:* Add Display Currency as a per-user ISO-4217 code owned by `user-service`. It is persisted with default `USD`, updated through `PATCH /users/{id}/display-currency`, and published to the compacted `user.display-currency` topic as `UserDisplayCurrencyUpdated`.

*Consequences:* Currency preference changes propagate through Kafka and remain display-only: holdings, orders, and snapshots stay USDT-denominated. The trade-off is a new compacted topic and the need to distinguish Display Currency from any future accounting unit of account.

== ADR-0029: FX-Rate-Service as Reference Data Context <adr-0029>

*Status:* Accepted

*Context:* Display Currency conversion needs FX rates. These are slow-moving reference data from an HTTP provider, unlike Binance WebSocket price ingestion owned by `market-data-service`.

*Decision:* Introduce `fx-rate-service` in a new Reference Data bounded context. It polls a public FX provider every five minutes, translates supported pairs, and publishes `FxRate` events to the compacted `reference.fx.rate` topic.

*Consequences:* Market data remains focused on crypto price ingestion, while slow-moving reference data has a clear owner. The trade-off is an additional deployable and a dependency on a free public provider, mitigated by compacted last-known rates.

== ADR-0030: Stream-Table Join for Price Localisation <adr-0030>

*Status:* Accepted

*Context:* USDT price streams can be converted on read or enriched stream-side. The project scope values demonstrating the stream-table join pattern in a customer-facing flow.

*Decision:* Record the target design for a Kafka Streams enrichment app that joins `crypto.price.clean` with the `reference.fx.rate` GlobalKTable and emits `LocalizedPrice` to `crypto.price.localized`, keyed by symbol. Each event carries the source USDT price plus a map of supported display currencies. The current implementation keeps valuation and OHLC on `crypto.price.raw` until the clean-price stream is available.

*Consequences:* The ADR fixes the intended localized-price contract and join shape, while the shipped system still performs implemented portfolio valuation and OHLC processing from raw USDT prices. The trade-off of the target design is another streams app, topic, and failure surface, but the topology is simple and scales with the supported currency set.

== ADR-0031: Venue-Native OHLC with Read-Time Conversion <adr-0031>

*Status:* Accepted

*Context:* OHLC bars aggregate USDT-denominated price ticks, but Display Currency raises whether bars should be emitted per currency, as a map-per-bar, or venue-native with conversion at read time. The scope also needs to demonstrate suppress-on-window-close semantics.

*Decision:* Emit closed OHLC bars venue-native in USDT on `crypto.ohlc.1m`, `crypto.ohlc.5m`, and `crypto.ohlc.1h`. The topology uses event-time tumbling windows and `suppress(untilWindowCloses)`, while display conversion happens at API read time using current FX rates.

*Consequences:* Each OHLC event represents a final closed bar, and topic count grows only with interval count. The trade-off is that historical-FX accuracy is out of scope; charts are rescaled by current FX rates rather than reconstructed with event-time FX.

== ADR-0032: Avro and Confluent Schema Registry for Derived Events <adr-0032>

*Status:* Accepted

*Context:* New derived events such as `FxRate`, `PortfolioValue`, `Ohlc`, `CoinMetadata`, and `UserDisplayCurrencyUpdated` cross multiple service ownership boundaries. `LocalizedPrice` is also defined as the ADR-0030 target contract, although the corresponding stream has not shipped yet. Registryless Avro is too informal for that level of schema evolution.

*Decision:* Adopt Avro with Confluent Schema Registry for those derived event types and add `schema-registry` to Docker Compose. Existing JSON topics remain JSON, and existing registryless Market Scout Avro topics are not migrated as part of this decision.

*Consequences:* New cross-service derived contracts gain central schema compatibility checks and generated type safety. The trade-off is one additional infrastructure dependency and build-time Avro code generation in services that use the registered contracts.

== ADR-0033: Coin Metadata Enrichment via GlobalKTable <adr-0033>

*Status:* Accepted

*Context:* OHLC consumers need display metadata such as asset names, logos, market-cap rank, base asset, and quote asset. Fetching that metadata synchronously from dashboard read paths would undermine the read-time cache pattern.

*Decision:* Add `coin-metadata-service` in the Reference Data context. It polls CoinGecko, publishes `CoinMetadata` to the compacted `reference.crypto.metadata` topic, and the OHLC topology joins closed bars against a `GlobalKTable` before publishing enriched `Ohlc` records.

*Consequences:* Dashboard consumers receive bars that already carry rendering metadata, and the OHLC scope demonstrates stream-table joins alongside windowing and suppress. The trade-off is schema growth on `Ohlc`, a static symbol-to-CoinGecko mapping, and dependence on a rate-limited public provider.

== ADR-0034: Portfolio Valuation Streams App inside Portfolio-Service <adr-0034>

*Status:* Accepted

*Context:* Portfolio valuation must continuously compute per-user value and expose it through interactive queries while demonstrating table-table join, multiphase repartitioning, and local state. The open questions were service placement, holdings source, and price source.

*Decision:* Host the valuation topology inside `portfolio-service` with application ID `portfolio-service-valuation`. It replays `transaction.order.approved` into a holdings KTable, uses `crypto.price.raw` as the USDT price source, keeps `at_least_once` processing, and exposes `GET /portfolios/{userId}/streams-value` beside the existing Postgres-backed endpoint.

*Consequences:* Scope 04 patterns are demonstrated in one user-facing flow, and rebuilding from the event log is explicit. The trade-off is that holdings exist as both a Postgres projection and a Streams state-store projection, plus new Kafka Streams and RocksDB runtime requirements in `portfolio-service`.

== ADR-0035: Loop Ops Resolution Back into the `placeOrder` Happy Path <adr-0035>

*Status:* Accepted; partially supersedes ADR-0018.

*Context:* ADR-0018 routed deterministic approval and publication failures to ops tasks that terminated the process after acknowledgement. This surfaced failures but prevented the user-facing closure email and, in the publish-failure case, left the process incomplete even though Kafka publication had already happened.

*Decision:* After ops completion, the process re-enters the happy path asymmetrically. Approval errors loop back to the approve step so the atomic approve-with-outbox write can retry; publish errors skip forward to the executed-email step because the Kafka event has already been published.

*Consequences:* Orders can still complete through the happy-path end event after human intervention, and premature ops closure self-heals by surfacing the same task again. The trade-off is a more nuanced BPMN model and a permanent audit anomaly if an outbox row was missing after Kafka publication.

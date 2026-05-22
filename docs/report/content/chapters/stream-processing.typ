= Kafka Streams Extensions <kafka-streams-extensions>

This chapter describes the second-half stream-processing extension of CryptoFlow. The first report chapters describe an event-driven microservice system where Kafka transports facts between bounded contexts (@adr-0001). The extension work adds continuously running Kafka Streams topologies that filter, translate, join, window, aggregate, and materialize those facts into new event streams and queryable local state.

The structure follows the lecture progression from event streams to stateful and windowed topologies, then maps the concepts to the implemented services. The main implementation areas are Market Scout and transaction matching on Cyril's side, and reference data, OHLC aggregation, and portfolio valuation on Ioannis' side.

== From Event Streams to Stream Processing Topologies

Lecture 07 framed an event stream as an unbounded, ordered, immutable, and replayable sequence of facts. This changes the design problem: the system does not first store all data and later run a finite query. Instead, the query is a long-running topology that continuously receives events, updates local state if needed, and emits derived events.

CryptoFlow uses that model in three places:

/ Source streams: External systems are converted into Kafka topics. Binance ticker events feed `crypto.price.raw` (@adr-0006), Binance partial book depth events feed `crypto.scout.raw` (@adr-0021), the FX provider feeds `reference.fx.rate` (@adr-0029), and CoinGecko metadata feeds `reference.crypto.metadata` (@adr-0033).

/ Processing topologies: Kafka Streams applications transform those topics into derived topics. Market Scout derives ask quotes and opportunities (@adr-0022), Transaction Service matches bids with asks (@adr-0027), Market Data Service builds OHLC bars (@adr-0031), and Portfolio Service computes current portfolio values (@adr-0034).

/ Materialized views: Some topologies persist local state stores. These stores are not side databases owned by another service; they are rebuildable views derived from Kafka input topics and their changelogs. Portfolio value and Market Scout dashboard state are exposed through interactive queries.

The stream-table duality is the common mental model. A `KStream` represents changes over time. A `KTable` represents the latest state after those changes have been applied. CryptoFlow uses both forms: prices become a latest-price table, holdings become a holdings table, FX and metadata topics are compacted reference tables, and windowed OHLC stores hold finite event-time aggregates.

== Stateless Stream Processing

Stateless processing handles each event independently. This covers the single-event patterns from Lecture 08: filter, translator, splitter, router, and idempotent reader or writer. The first stage of the stream extension is intentionally simple because stateless processors scale and replay cleanly.

The `fx-rate-service` is the reference-data example. It polls a public FX provider on a five-minute schedule, validates the response, translates one provider response into one `FxRate` per currency pair, and publishes those records to the compacted `reference.fx.rate` topic. The compacted topic is the cache. Consumers materialize it locally and never call the FX provider on the event path, which is the Reference Data decision in @adr-0029.

#figure(
  image("figures/fx-rate-ingestion.svg", width: 90%),
  caption: [FX rate ingestion as single-event processing: scheduled fetch, filter, translator, compacted reference topic],
) <fig:stream-fx-ingestion>

The `coin-metadata-service` applies the same shape to slower-moving crypto metadata (@adr-0033). It polls CoinGecko, maps configured Binance symbols such as `BTCUSDT` to metadata such as base asset, quote asset, name, image URL, and market-cap rank, then publishes `CoinMetadata` records to `reference.crypto.metadata`. Downstream topologies can materialize this compacted topic as a `GlobalKTable`.

Cyril's ingestion path starts with `market-partial-book-ingestion-service`. It subscribes to Binance USD-M Futures Partial Book Depth streams (@adr-0021) and publishes raw depth updates as JSON to `crypto.scout.raw`. This topic is the replay boundary for Scout. Keeping the source record as raw JSON preserves observability and lets a fresh Scout application id rebuild derived scout topics from the retained raw feed (@adr-0022, @adr-0024).

The `market-order-scout-service` then performs stateless stream processing over `crypto.scout.raw`:

```text
crypto.scout.raw (RawOrderBookDepthEvent, JSON)
    -> filter events without asks
    -> split ask levels into AskQuote records
    -> translate AskQuote into MatchableAsk
    -> filter AskQuote by configured ask threshold
    -> translate threshold hits into AskOpportunity
```

`AskQuote`, `AskOpportunity`, and `ScoutWindowSummary` are scout-owned Avro records (@adr-0022). `MatchableAsk` is different: @adr-0026 makes it the shared cross-service ask contract published to `crypto.scout.matchable-asks`. That keeps Transaction Service independent from Scout's dashboard-oriented event shapes.

== Stateful Processing and Local State Stores

Lecture 09 adds the core stateful idea: when processing depends on previous events, records with the same key must reach the same task, and state must be persisted in local stores backed by changelog topics. CryptoFlow uses state stores for matching, windowing, portfolio valuation, and dashboard reads.

The most direct stateful topology is transaction matching. The previous trading flow matched pending orders against ticker prices kept in heap memory. @adr-0027 replaces that with a Kafka Streams topology in `transaction-service`:

```text
transaction.buy-bids                crypto.scout.matchable-asks
    key: symbol                         key: symbol
    value: BuyBid                       value: MatchableAsk
        │                                   │
        └──────────── normalize symbol ─────┘
                            │
                            ▼
                merge bid and ask candidates
                            │
                            ▼
         ValueTransformerWithKey + persistent stores
             pending-buy-bids-by-symbol
             matched-transactions
             allocated-asks
                            │
                            ▼
              transaction.order-matched
```

The topology keeps pending bids by symbol, remembers matched transaction ids, and remembers allocated ask quote ids. This makes matching restartable and prevents duplicate match decisions. A `MatchableAsk` can be allocated to at most one still-pending bid, and a transaction that has already matched is ignored on replay.

The portfolio valuation topology uses state stores at a higher level of abstraction (@adr-0034). It consumes approved orders and prices, materializes both as tables, computes position values, then groups positions into a user-level aggregate:

```text
transaction.order.approved
    -> holdings KTable, key = userId|symbol

crypto.price.raw
    -> prices KTable, key = symbol

holdings FK join prices
    -> position-value KTable, key = userId|symbol
    -> groupBy userId
    -> portfolio-value-store
    -> portfolio.value.updated
```

The `portfolio-value-store` backs `GET /portfolios/{userId}/streams-value`. This demonstrates the Lecture 09 interactive-query pattern: the service reads the local Kafka Streams state store for the current materialized value instead of issuing a synchronous request to another service.

== Time, Windows, Grace, and Suppression

Lecture 10 distinguishes event time, ingestion time, and processing time. CryptoFlow uses event time when business meaning depends on when the market event happened, not when the processor happened to receive it.

The Market Scout summary is a simple tumbling-window aggregation. After `AskOpportunity` records are produced, the topology groups by symbol, applies a configured window size, and aggregates the opportunity count and minimum ask price into `ScoutWindowSummary`. It also materializes a dashboard stats store so the Scout dashboard can read the latest summary state.

The OHLC topology is the more complete windowing example. In the final target architecture, @adr-0031 and the scope document name `crypto.price.clean` as the canonical input. The current implementation reads `crypto.price.raw` because the scope-02 price-sanity stream has not shipped yet. This is an implementation-stage deviation, not the final doctrine; the code documents the source swap as a one-line configuration change.

```text
crypto.price.raw
    -> extract event timestamp from CryptoPriceUpdatedEvent
    -> groupByKey(symbol)
    -> tumbling window 1m, grace configured
    -> aggregate open/high/low/close/tickCount in ohlc-1m-store
    -> suppress until window closes
    -> join with reference.crypto.metadata GlobalKTable
    -> crypto.ohlc.1m

same source stream
    -> analogous 5m and 1h pipelines
    -> crypto.ohlc.5m / crypto.ohlc.1h
```

The grace period keeps a window open long enough to absorb bounded late ticks. `suppress(untilWindowCloses)` prevents downstream consumers from receiving every intermediate candle update. They receive one closed bar per `(symbol, window)` after the window and grace period are complete. This matches the semantics of a closed candlestick used by charts and future indicator computations (@adr-0031).

Transaction matching also uses event-time validity, but as business logic inside a stateful transformer rather than as a DSL windowed join. A bid is valid from `BuyBid.createdAt` until `createdAt + 30s`, with a five-second fallback margin aligned with the Camunda timeout. A matching ask must have `ask.eventTime` inside that interval, a price at or below the bid price, and enough quantity (@adr-0027).

== Joins, Repartitioning, and Interactive Queries

CryptoFlow uses three join variants from Lectures 08 and 09.

First, the OHLC topology uses a stream-table join against `reference.crypto.metadata` (@adr-0033). It materializes metadata as a `GlobalKTable`, so each stream task can enrich closed bars locally without requiring co-partitioning between price ticks and metadata. Missing metadata does not block the bar; the left join emits the OHLC record with nullable metadata fields.

Second, the portfolio valuation topology uses a foreign-key table-table join (@adr-0034). Holdings are keyed by `userId|symbol`, while prices are keyed by `symbol`. The `Holding` value carries the symbol so Kafka Streams can join each holding row to the current price row. The result is a `PositionValue` table keyed by `userId|symbol`.

Third, portfolio valuation demonstrates multiphase repartitioning. A per-position table is the right representation for joining holdings to prices, but the dashboard needs a per-user sum. The topology therefore groups the position values by `userId` and aggregates into `portfolio-value-store`. Kafka Streams handles the repartition topic needed to move all positions for one user to the same task.

Interactive queries are used where the materialized result is directly useful to the local service API. `portfolio-service` exposes `GET /portfolios/{userId}/streams-value` from `portfolio-value-store`. `market-order-scout-service` exposes dashboard statistics from `market-scout-dashboard-stats`. The development deployment runs one instance of each service, so multi-instance host discovery and redirect logic are intentionally out of scope.

#figure(
  caption: [Implemented stream-processing patterns in CryptoFlow],
  table(
    columns: (1.15fr, 2.2fr, 2.2fr),
    [*Pattern*], [*Implementation*], [*Resulting topic or store*],
    [Single-event processing], [FX filter/translator, metadata polling, ask filtering and translation], [`reference.fx.rate`, `reference.crypto.metadata`, `crypto.scout.ask-quotes`],
    [Processing with local state], [Transaction matching stores, OHLC window stores, portfolio stores], [`pending-buy-bids-by-symbol`, `ohlc-*` stores, `portfolio-value-store`],
    [Stream-table join], [Closed OHLC bars enriched with coin metadata (@adr-0033); FX/localized-price design in @adr-0030], [`crypto.ohlc.1m/5m/1h`],
    [Table-table join], [Portfolio holdings joined with latest prices by symbol], [`position-value` KTable],
    [Multiphase repartitioning], [Portfolio positions re-keyed from `userId|symbol` to `userId`], [`portfolio-value-store`],
    [Windowing], [Market Scout summaries, OHLC bars, bid validity interval], [`crypto.scout.window-summary`, `crypto.ohlc.*`, `transaction.order-matched`],
    [Interactive queries], [Portfolio value endpoint and Market Scout dashboard], [`GET /portfolios/{userId}/streams-value`, Scout dashboard state],
    [Reprocessing], [Fresh application id or offset reset rebuilds derived state from input topics], [Rebuilt state stores and derived topics],
  ),
) <tab:implemented-stream-patterns>

== Event Contracts and Reprocessing

The stream extension uses three serialization strategies because the topics have different ownership and evolution needs.

/ JSON for replay boundaries and legacy/raw topics: `crypto.price.raw`, `crypto.scout.raw`, and existing order/user topics remain readable JSON (@adr-0003, @adr-0022). They are useful for debugging, demo inspection, and replaying source events into newer derived topologies.

/ Registryless Avro for scout-owned derived topics: `AskQuote`, `AskOpportunity`, and `ScoutWindowSummary` are local to `market-order-scout-service` (@adr-0022). They use Avro schemas but do not need Schema Registry because no independent bounded context depends on them as stable contracts.

/ Schema Registry Avro for cross-service derived contracts: `FxRate`, `CoinMetadata`, `Ohlc`, `PortfolioValue`, `UserDisplayCurrencyUpdated`, and the shared `MatchableAsk` contract are schema-managed or shared because they cross service boundaries or are consumed by independently deployable components. @adr-0032 records the general decision for registry-backed Avro derived events, while @adr-0026 records the narrower `MatchableAsk` contract.

Reprocessing follows the same rule across topologies: input topics are the source of truth for derived state. A fresh Kafka Streams `application.id`, or an offset reset for the existing app id during development, lets a topology rebuild its local stores and derived outputs from retained input records. This is useful for OHLC bars, portfolio values, and Scout summaries. The practical limit is topic retention: raw scout and market topics are intentionally short-lived in the development broker to protect disk space, while compacted reference topics retain latest state by key.

== Implementation Insights

The second-half work made a clear distinction between facts, reference data, and derived views. Partial book depth and raw prices are facts from market sources. FX rates and coin metadata are reference data, represented as compacted topics. OHLC bars, ask opportunities, matched orders, and portfolio values are derived views produced by stream-processing applications.

The Market Scout split is important architecturally. `market-partial-book-ingestion-service` owns external connectivity and raw capture. `market-order-scout-service` owns the stream-processing topology and derived ask contracts. @adr-0023 keeps these responsibilities separate, and @adr-0024 gives Scout its own application id so its state and offsets can evolve independently.

The transaction matching topology moved order execution closer to the event-processing model taught in the lectures. It no longer treats ticker prices as executable liquidity and no longer loses pending orders on service restart. Persistent stores and deterministic price-time priority make the result replayable: highest bid price wins, then earliest bid creation time, then transaction id for deterministic tie-breaking.

The OHLC implementation shows how course-scope sequencing affects architecture. The intended clean-price input is documented, but the implemented topology reads `crypto.price.raw` until the price-sanity stream exists. Recording this as a stage deviation avoids turning a temporary source choice into architecture doctrine.

The portfolio valuation topology is the densest pattern demonstration (@adr-0034). It combines stream-table duality, a foreign-key table-table join, repartitioning, local state stores, a compacted output topic, and an interactive-query endpoint. The result is still eventually consistent, but the read path is fast and local, and the state can be reconstructed from Kafka inputs.

Finally, the contract choices reflect coupling. Raw JSON keeps the platform inspectable at replay boundaries (@adr-0003). Avro without a registry is sufficient for service-local derived streams (@adr-0022). Schema Registry Avro is used where independent services need durable schema evolution (@adr-0032). This keeps the implementation pragmatic while still aligning the cross-service contracts with the lecture material on schemas and data contracts.

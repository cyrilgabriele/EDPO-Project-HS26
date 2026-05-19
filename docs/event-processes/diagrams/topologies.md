# Topologies for the Display Currency, FX, Portfolio and OHLC scopes

Diagrams supporting [00-display-currency-cross-context.md](../00-display-currency-cross-context.md) and ADRs 0028 through 0032. All Mermaid; renders in VS Code (with the built-in Markdown preview or the Mermaid extension), JetBrains IDEs (with the Mermaid plugin), GitHub, and GitLab.

## 1. fx-rate-service (scope 01): stateless producer pipeline

Polls a public FX provider on a timer, filters invalid responses, fans out into one `FxRate` per quote currency, publishes to the compacted topic. See [ADR-0029](../../adrs/0029_fx_rate_service_as_reference_data_context.md).

```mermaid
flowchart LR
    EXT["FX provider<br/>(exchangerate.host)"]
    POLL["Scheduled fetch<br/>(5 min timer)"]
    FILTER["Event Filter<br/>drop malformed or stale"]
    XLATE["Event Translator<br/>one FxRate per quote ccy<br/>ISO-4217 normalize<br/>stamp fetchedAt, validFromAt"]
    SINK[("reference.fx.rate<br/>compacted, key=USDx, Avro")]

    EXT --> POLL --> FILTER --> XLATE --> SINK
```

## 2. Scope 03 FX price enrichment: stream-table join

Joins `crypto.price.clean` with the FX GlobalKTable, emits a `LocalizedPrice` carrying a map of values per supported currency. See [ADR-0030](../../adrs/0030_stream_table_join_for_price_localization.md).

```mermaid
flowchart LR
    SRC[("crypto.price.clean<br/>KStream, key=symbol")]
    FX[("reference.fx.rate<br/>GlobalKTable, key=USDx")]
    MAP["mapValues<br/>for each ccy in {USD,EUR,CHF,GBP}<br/>compute priceUsdt × FX(USDx)"]
    OUT[("crypto.price.localized<br/>key=symbol, prices map, Avro")]

    SRC --> MAP
    FX -. lookup USDx for each ccy .-> MAP
    MAP --> OUT
```

## 3. Scope 05 OHLC: tumbling-window aggregation with suppress

Aggregates `crypto.price.clean` into closed OHLC bars per symbol per window, USDT-denominated. See [ADR-0031](../../adrs/0031_venue_native_ohlc_with_read_time_conversion.md).

```mermaid
flowchart LR
    SRC[("crypto.price.clean<br/>KStream, key=symbol")]
    TS["Custom TimestampExtractor<br/>reads sourceTimestamp"]
    GBK["groupByKey<br/>(by symbol)"]
    WIN["windowedBy Tumbling<br/>1m / 5m / 1h<br/>grace 10s / 30s / 2min"]
    AGG["aggregate<br/>OHLC init/update per tick<br/>(open, high, low, close, tickCount)"]
    SUP["suppress<br/>untilWindowCloses(unbounded)"]
    M["map: Windowed&lt;symbol&gt; → symbol@windowStart"]
    O1[("crypto.ohlc.1m<br/>USDT, Avro, 7d retention")]
    O5[("crypto.ohlc.5m<br/>USDT, Avro, 30d retention")]
    O1H[("crypto.ohlc.1h<br/>USDT, Avro, 365d retention")]

    SRC --> TS --> GBK --> WIN --> AGG --> SUP --> M
    M --> O1
    M --> O5
    M --> O1H
```

Each interval is its own pipeline instance with its own window size, grace, and state store; the diagram collapses the three siblings for clarity.

## 4. Display Currency propagation (User Identity context)

user-service is the source of truth; both Portfolio and Trading consume the compacted topic as a KTable so the value is available at API read time without an HTTP call. See [ADR-0028](../../adrs/0028_display_currency_as_user_identity_data.md).

```mermaid
flowchart LR
    USR["End user"]
    SIGNUP["user-service<br/>create user"]
    PATCH["user-service<br/>PATCH /users/{id}/display-currency"]
    DB[("user table<br/>display_currency CHAR(3)")]
    EMIT["UserDisplayCurrencyUpdated emitter"]
    TOPIC[("user.display-currency<br/>compacted, key=userId, Avro")]
    PF["portfolio-service<br/>DisplayCurrencyCache (KTable)"]
    TR["transaction-service<br/>DisplayCurrencyCache (KTable)"]

    USR -->|signup| SIGNUP
    USR -->|change preference| PATCH
    SIGNUP -->|default 'USD'| DB
    PATCH -->|validated ISO-4217| DB
    SIGNUP --> EMIT
    PATCH --> EMIT
    EMIT --> TOPIC
    TOPIC --> PF
    TOPIC --> TR
```

## 5. Read-time conversion: portfolio valuation request

Display-only doctrine means conversion happens at the API boundary, not inside the streams app. Portfolio looks up the user's Display Currency in its KTable, looks up each holding's `LocalizedPrice` in its KTable, picks the right slot in the `prices` map, and sums. The buy-time quote flow in transaction-service is structurally identical.

```mermaid
sequenceDiagram
    participant U as User (DC=CHF)
    participant PFS as portfolio-service
    participant DCC as DisplayCurrencyCache<br/>(KTable from user.display-currency)
    participant LPC as LocalizedPriceCache<br/>(KTable from crypto.price.localized)
    participant DB as Postgres (holdings)

    U->>PFS: GET /portfolios/{userId}
    PFS->>DCC: lookup(userId)
    DCC-->>PFS: "CHF"
    PFS->>DB: select holdings where user_id = userId
    DB-->>PFS: [(BTC, 0.5), (ETH, 2.0), ...]
    loop per holding
        PFS->>LPC: lookup(symbol)
        LPC-->>PFS: LocalizedPrice{prices: {USD, EUR, CHF, GBP}}
        Note over PFS: value_CHF = qty × prices["CHF"]
    end
    PFS-->>U: total in CHF and per-position breakdown
```

## 6. Cross-context overview: how the new topics connect

Solid arrows are the runtime data flow on the hot path. Dotted arrows show that Portfolio and Trading could hold the FX KTable directly for any read that does not go through `LocalizedPrice` (currently none, but the option is there per ADR-0029).

```mermaid
flowchart TB
    BIN["Binance<br/>(WebSocket)"]
    FXP["FX provider<br/>(HTTP)"]

    subgraph MD["Market Data Context"]
        MDS["market-data-service<br/>(ingest + sanity)"]
        S03["scope-03 streams app<br/>(stream-table join)"]
        S05["scope-05 streams app<br/>(OHLC windows)"]
    end

    subgraph RD["Reference Data Context"]
        FXS["fx-rate-service"]
    end

    subgraph UI["User Identity Context"]
        US["user-service"]
    end

    subgraph PF["Portfolio Context"]
        PFS["portfolio-service"]
    end

    subgraph TR["Trading Context"]
        TRS["transaction-service"]
    end

    BIN -->|ticks| MDS
    FXP -->|poll| FXS

    MDS -->|crypto.price.raw| MDS
    MDS -->|crypto.price.clean| S03
    MDS -->|crypto.price.clean| S05

    FXS -->|reference.fx.rate compacted| S03
    FXS -.->|reference.fx.rate compacted| PFS
    FXS -.->|reference.fx.rate compacted| TRS

    S03 -->|crypto.price.localized| PFS
    S03 -->|crypto.price.localized| TRS

    S05 -->|crypto.ohlc.1m / 5m / 1h| PFS
    S05 -->|crypto.ohlc.1m / 5m / 1h| TRS

    US -->|user.display-currency compacted| PFS
    US -->|user.display-currency compacted| TRS
```

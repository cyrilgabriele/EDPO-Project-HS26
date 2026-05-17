# 00. Display Currency, FX, Portfolio and OHLC Cross-Context Spec

**Type:** cross-context narrative &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** ready

## Purpose

This spec stitches together the user-facing feature ("see my portfolio and buy-time quotes in my chosen currency, plus OHLC charts") across the four bounded contexts it touches: Reference Data, User Identity, Portfolio, and Trading. The per-scope drafts (01, 03, 04, 05) cover topology mechanics for one box at a time; this document fixes the contracts and decisions that span them.

It captures the resolved decisions, points at the load-bearing ADRs, and lists the work needed end-to-end. Read this first; read the per-scope drafts when implementing a specific box.

## Why this matters

Display Currency is the first feature on the platform that requires every read API to know who is asking. Until now, both portfolio-service and transaction-service produced USDT-only responses that were identical for every user. Making the response per-user without re-denominating storage is a small change at each box and a large coordination concern between them. This spec is where that coordination lives.

## Topology diagrams

All six topologies (fx-rate-service, scope 03 enrichment, scope 05 OHLC, Display Currency propagation, read-time conversion sequence, cross-context overview) are in [diagrams/topologies.md](diagrams/topologies.md).

## Resolved decisions and pointers

| # | Decision | ADR |
|---|---|---|
| D1, D2, D3, D8 | Display Currency: display-only, dedicated compacted topic, default `'USD'` at creation | [ADR-0028](../adrs/0028_display_currency_as_user_identity_data.md) |
| D4 | fx-rate-service as a Reference Data bounded context | [ADR-0029](../adrs/0029_fx_rate_service_as_reference_data_context.md) |
| D5 | Stream-table join enrichment (scope 03), broadcast map of currencies | [ADR-0030](../adrs/0030_stream_table_join_for_price_localization.md) |
| D6, D9 | OHLC bars: venue-native, closed-bar (suppress) emission | [ADR-0031](../adrs/0031_venue_native_ohlc_with_read_time_conversion.md) |
| D7 | Avro + Confluent Schema Registry for new derived events | [ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md) |

## Context map delta

The existing `docs/diagrams/context-map.md` describes five contexts: Market Data, User Identity, Onboarding, Trading, Portfolio. This spec adds a **Reference Data Context** owning fx-rate-service.

New edges added by this spec:

- `Reference Data` → `Market Data` (scope-03 enrichment app consumes `reference.fx.rate` as a GlobalKTable).
- `Reference Data` → `Portfolio` (FX rates for read-time conversion).
- `Reference Data` → `Trading` (FX rates for read-time conversion).
- `User Identity` → `Portfolio` (Display Currency).
- `User Identity` → `Trading` (Display Currency).
- `Market Data` → `Portfolio` (`crypto.price.localized` replaces direct `crypto.price.raw` consumption for valuation).
- `Market Data` → `Trading` (`crypto.price.localized` for buy-time quotes, new consumer).

## New / changed Kafka topics

All new events are Avro per ADR-0032; existing topics keep their current serialization.

| Topic | Cleanup | Partitions | Key | Value (Avro) | Producer | Consumers |
|---|---|---|---|---|---|---|
| `reference.fx.rate` | compact | 1 | currency-pair string (`"USDCHF"`) | `FxRate` | fx-rate-service | scope-03 app, portfolio, trading |
| `user.display-currency` | compact | 1 | userId | `UserDisplayCurrencyUpdated` | user-service | portfolio, trading |
| `crypto.price.localized` | delete (1 h) | match `crypto.price.clean` | symbol | `LocalizedPrice` | scope-03 streams app | portfolio, trading |
| `portfolio.value.updated` | compact | (match userId scale) | userId | `PortfolioValue` (USDT total + breakdown) | portfolio-service | dashboard read APIs |
| `crypto.ohlc.1m` | delete (7 d) | by symbol | symbol | `Ohlc` (USDT) | scope-05 streams app | dashboard read APIs |
| `crypto.ohlc.5m` | delete (30 d) | by symbol | symbol | `Ohlc` | scope-05 streams app | dashboard read APIs |
| `crypto.ohlc.1h` | delete (365 d) | by symbol | symbol | `Ohlc` | scope-05 streams app | dashboard read APIs |

Existing topics (`crypto.price.raw`, `crypto.price.clean`, `transaction.order.approved`, `user.confirmed`, …) unchanged.

## Avro schemas (sketch, finalize under `shared-events/src/main/avro/`)

```
FxRate
  base:        string                   // "USD"
  quote:       string                   // "CHF"
  rate:        double                   // quote per 1 base
  source:      string                   // "exchangerate.host"
  fetchedAt:   timestamp-millis
  validFromAt: timestamp-millis

UserDisplayCurrencyUpdated
  userId:          string
  displayCurrency: string               // ISO-4217, one of {USD,EUR,CHF,GBP}
  updatedAt:       timestamp-millis

LocalizedPrice
  symbol:           string
  priceUsdt:        double
  prices:           map<string, double> // ISO-4217 → value in that currency
  sourceVenue:      string
  sourceTimestamp:  timestamp-millis
  enrichedAt:       timestamp-millis

PortfolioValue
  userId:      string
  totalUsdt:   double
  breakdown:   array<PositionValue>     // symbol, quantity, valueUsdt
  asOf:        timestamp-millis

Ohlc
  symbol:       string
  intervalSec:  int                     // 60, 300, 3600
  windowStart:  timestamp-millis
  windowEnd:    timestamp-millis
  open:         double                  // USDT
  high:         double                  // USDT
  low:          double                  // USDT
  close:        double                  // USDT
  tickCount:    int                     // proxy for volume (see Flagged ambiguities)
```

## Service-by-service responsibilities

### fx-rate-service (new)

- HTTPS poll against the configured FX provider on a fixed timer (default: `exchangerate.host/latest`, 5 minute interval).
- Filter malformed/stale responses.
- Translate into one `FxRate` per quote currency in the supported set.
- Publish to `reference.fx.rate`, keyed by `${base}${quote}` (string).
- Stateless. The compacted topic is the cache.
- See `01-fx-rate-ingestion.md` for the topology detail.

### user-service (extended)

- New column `display_currency` on the user table (`CHAR(3) NOT NULL DEFAULT 'USD'`).
- On user creation, persist `'USD'` and publish a `UserDisplayCurrencyUpdated` event.
- New endpoint `PATCH /users/{id}/display-currency` accepting an ISO-4217 code from the supported set; persists and publishes.
- Migration file: `user-service/src/main/resources/db/migration/V?__add_display_currency.sql`.

### scope-03 streams app (new, hosted inside market-data-service as a streams module; see Flagged ambiguities)

- Build:
  - `KStream<String, CleanPriceTick>` from `crypto.price.clean`.
  - `GlobalKTable<String, FxRate>` from `reference.fx.rate`.
- For each tick: compute `prices` map = `{currency → priceUsdt × FX(USD→currency)}` for every currency in the supported set.
- Emit `LocalizedPrice` to `crypto.price.localized`, keyed by symbol.

### portfolio-service (extended)

- Replace `LocalPriceCache` with a `LocalizedPrice` consumer keyed by symbol. The cache now holds per-currency values for each symbol.
- New `DisplayCurrencyCache` consumes `user.display-currency` (KTable materialization, keyed by userId).
- `GET /portfolios/{userId}` valuation logic:
  1. Look up the user's `displayCurrency` from `DisplayCurrencyCache`.
  2. For each holding, look up the latest `LocalizedPrice` and pick `prices[displayCurrency]`.
  3. Multiply by quantity and sum.
- Storage (`portfolio`, `holding`) unchanged: quantities and `averagePurchasePrice` stay USDT.

### transaction-service (extended)

- New buy-time-quote read path: an endpoint that returns the latest market price in the requesting user's Display Currency. Pattern mirrors portfolio's read-time conversion.
- Consumes `crypto.price.localized` and `user.display-currency` for the same reason portfolio does.
- Order placement itself (USDT-internal limit prices, the existing `transaction.buy-bids` flow) is unchanged.

### scope-05 streams app (new)

- See `05-ohlc-candles.md` for topology detail.
- Source: `crypto.price.clean` (NOT `crypto.price.localized`, since OHLC bars stay venue-native per ADR-0031).
- Custom event-time `TimestampExtractor` on the input.
- Tumbling windows: 1 m / 10 s grace, 5 m / 30 s grace, 1 h / 2 min grace.
- `suppress(untilWindowCloses(unbounded()))` before sink: one bar per symbol per window, emitted after the window closes.
- Sink: `crypto.ohlc.{intervalLabel}`, keyed by symbol.

## Operational defaults

These are configurable; pick a starting point, change as needed.

- **Supported Display Currencies:** `{USD, EUR, CHF, GBP}`. USD is the FX anchor and the default. CHF and EUR reflect team geography; GBP for breadth.
- **FX provider:** `exchangerate.host` (free, no API key). Documented fallback: ECB reference rates.
- **FX poll cadence:** 5 minutes.
- **OHLC intervals:** 1 m, 5 m, 1 h.
- **OHLC grace periods:** per `05-ohlc-candles.md`.
- **Schema Registry:** `http://schema-registry:8081` inside the Docker network, `http://localhost:8090` from the host (Confluent image in `docker-compose`; host port 8081 is taken by market-data-service).

## End-to-end verification

1. **Schema Registry up:** `curl http://localhost:8090/subjects` returns `[]` then grows after first publishes.
2. **fx-rate-service publishes:** within one poll interval after boot, `kafka-console-consumer --topic reference.fx.rate --from-beginning --property print.key=true` shows one record per supported pair (`USDCHF`, `USDEUR`, `USDGBP`, plus `USDUSD` if generated). Restart the broker; consumer with `--from-beginning` still sees the rates (compaction-as-cache).
3. **Display Currency lifecycle:** create user `alice` → consume `user.display-currency` keyed by `alice` showing `displayCurrency='USD'`. `PATCH /users/alice/display-currency` with body `{"displayCurrency":"CHF"}` → new record on the topic, same key, new value.
4. **LocalizedPrice fan-out:** subscribe to `crypto.price.localized`; observe each event carries `prices` map with all supported currencies, and that the USD value matches the source `priceUsdt`.
5. **End-to-end portfolio:** with `alice.displayCurrency='CHF'` and a holding of 0.5 BTC, `GET /portfolios/alice` returns `totalValue` denominated in CHF, computed as `0.5 × LocalizedPrice(BTC).prices[CHF]` for the latest BTC tick.
6. **End-to-end buy-time quote:** same user, new buy-quote endpoint returns BTC price in CHF.
7. **OHLC closed-bar semantics:** subscribe to `crypto.ohlc.1m` for 3 minutes. Expect at most one bar per symbol per minute, each appearing **after** the grace period elapses (no in-flight updates).
8. **Replay determinism:** stop the scope-05 app, reset its offsets to earliest on `crypto.price.clean`, restart. Replay-produced bars match the original run for all windows whose grace period has fully elapsed.

## Flagged ambiguities

- **Scope-05 `volume`.** Scope 02 omits volume from `CleanPriceTick` (Binance tick stream doesn't carry it). Scope 05's draft `Ohlc` schema still has a `volume` field that would always be null. **Resolution proposed in this spec:** drop `volume` from `Ohlc` and keep only `tickCount`. To be confirmed with scope-05 owner.
- **scope-03 host.** Teammate's draft doesn't name a service. **Proposed:** host scope-03 as a streams module inside market-data-service (it transforms market data and consumes `crypto.price.clean` which market-data-service produces). Alternative: a separate `price-enrichment-service`. To be confirmed with scope-03 owner.
- **`crypto.price.localized` retention.** Not stated in scope-03 draft. **Proposed:** delete-retention, ~1 hour. It's a derived stream consumed by stateful materializers and is replayable.

## Open questions deferred to implementation

- Whether transaction-service exposes a separate `/quote` endpoint or extends an existing one.
- Whether the buy-order form accepts limit prices in Display Currency (frontend converts) or in USDT (current behaviour).
- Whether `PortfolioValue` snapshots are emitted on every price tick or throttled to material-change events (decision belongs to scope 04).

## Related

- `01-fx-rate-ingestion.md`: fx-rate-service topology.
- `03-fx-price-enrichment.md`: scope-03 streams app.
- `04-portfolio-valuation.md`: portfolio aggregation, IQ.
- `05-ohlc-candles.md`: OHLC streams app.
- `06-technical-indicators.md`: downstream of 05; not yet wired into this spec.
- `docs/fx-rate-service/CONTEXT.md`: Reference Data context glossary.
- `docs/diagrams/context-map.md`: updated bounded-context view.

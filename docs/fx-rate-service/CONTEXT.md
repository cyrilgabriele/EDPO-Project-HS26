# Reference Data: fx-rate-service

fx-rate-service is the entry point for the Reference Data bounded context. It polls a public FX provider, publishes per-currency-pair rate events to a log-compacted Kafka topic, and feeds downstream consumers (the price-enrichment streams app, portfolio-service, transaction-service) without ever being on their hot read path.

## Language

**Reference Data**:
Slow-moving, externally-sourced facts the platform relies on but does not produce. FX rates are the first instance; future candidates include trading-pair metadata and exchange calendars.
_Avoid_: Market data (push-driven, exchange-native); Static data (suggests it never changes).

**FX Rate**:
A `quote-per-unit-of-base` ratio between two ISO-4217 currencies, sourced from an external provider with a `validFromAt` timestamp from the provider and a `fetchedAt` timestamp set at poll time.
_Avoid_: FX price (overloads "price" with the crypto-price domain); Conversion rate (too generic).

**Currency Pair**:
A directed pair of ISO-4217 codes, identified canonically as the concatenation `${base}${quote}` (for example `USDCHF`). The string concatenation is the Kafka key on `reference.fx.rate`.
_Avoid_: Symbol (already used for crypto trading pairs like `BTCUSDT`).

**Base Currency**:
The currency that quantifies "one unit" in a quoted FX Rate. The platform anchors on USD: all rates are published as `USD → quote` and the `prices` map in downstream `LocalizedPrice` events is computed from those.
_Avoid_: From currency, Source currency.

**Quote Currency**:
The currency whose units are being measured per one unit of the Base Currency. For the pair `USDCHF`, CHF is the Quote Currency and the rate value answers "how many CHF for one USD."
_Avoid_: To currency, Target currency, Destination currency.

**Display Currency**:
The per-user, ISO-4217 currency a user chose for presentation. Owned by user-service (see ADR-0028). The Reference Data context exposes the FX Rates needed to render values in any supported Display Currency, but does not itself know about users.
_Avoid_: Preferred Currency (fuzzy, does not say what it governs); Reporting Currency (implies persistent accounting weight); Local Currency (overloaded outside finance).

**Supported Currency Set**:
The finite list of ISO-4217 codes the platform currently allows as Display Currency. Initial set: `{USD, EUR, CHF, GBP}`. fx-rate-service fetches all `USD→x` pairs in this set; user-service validates `PATCH /users/{id}/display-currency` against it.
_Avoid_: Allowed currencies, Currency whitelist.

**Cache-as-Topic**:
The pattern where a compacted Kafka topic is the canonical store, and consumers materialize it as a KTable/GlobalKTable. The Reference Data context exposes FX Rates this way: there is no synchronous HTTP API for "what is the current FX rate?".
_Avoid_: FX cache (singular, implying one consumer-side cache when we have many).

## Relationships

- The **fx-rate-service** is the sole producer for the Reference Data context.
- The **fx-rate-service** writes to `reference.fx.rate`, one record per **Currency Pair**.
- Every consumer reads `reference.fx.rate` as a **Cache-as-Topic** (compacted, materialized as a GlobalKTable).
- The **Supported Currency Set** is a runtime configuration of fx-rate-service; it determines which pairs are fetched and published.
- A **Display Currency** chosen by a user must be a member of the **Supported Currency Set**; user-service performs this validation on `PATCH`.
- The **FX Rate** for the pair `USDx` is what enables conversion of any USDT-denominated platform value into the user's **Display Currency**, on the assumption that USD≈USDT for display purposes.

## Example dialogue

> **Dev:** "Can we expose a `GET /fx/USDCHF` endpoint on fx-rate-service?"
>
> **Domain expert:** "No. The Reference Data context is **Cache-as-Topic**, so consumers materialize `reference.fx.rate` as a GlobalKTable and serve their own reads from local state. A synchronous endpoint would put fx-rate-service on the hot path of every portfolio valuation, which is exactly what compaction is there to prevent (see ADR-0002 and ADR-0029)."

> **Dev:** "Why don't we just call it Conversion Rate?"
>
> **Domain expert:** "Because `crypto.price.localized` is also produced by 'converting' a USDT price into multiple currencies, and we want **FX Rate** to mean the input ratio from the provider, not the act of converting and not the converted value. The conversion happens at the API boundary; the **FX Rate** is the raw fact."

## Flagged ambiguities

- **USD vs USDT.** Crypto prices are quoted in USDT on Binance; FX rates are quoted in USD. The platform treats them as interchangeable for display purposes. This is acceptable for a trading dashboard but will look wrong on settlement-style reports. If "unit of account" semantics are added later (see ADR-0028 Consequences), this assumption needs revisiting.
- **Provider failure modes.** If the FX provider returns a stale or partial response, fx-rate-service filters it out (no publish). Consumers continue serving last-known rates from their GlobalKTable. There is no current alerting on prolonged provider outage; needs a downstream monitoring decision.

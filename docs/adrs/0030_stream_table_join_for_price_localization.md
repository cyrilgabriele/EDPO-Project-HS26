# 30. Use a stream-table join to localize the price stream

Date: 2026-05-16

## Status

Accepted

## Context

ADR-0028 commits to display-only conversion, and ADR-0029 puts FX rates on a compacted topic. Two architectural shapes can deliver per-currency prices to the read APIs:

1. **On-read conversion.** Portfolio and Trading each consume `reference.fx.rate` into a local cache, hold the USDT price cache they already have, and compute the conversion inside the HTTP handler on every request.
2. **Stream-side enrichment.** A Kafka Streams app joins the price stream with the FX KTable, emits a "localized" event carrying values per supported currency, and consumers pick the right slot at read time.

Option 1 is operationally simpler. Option 2 demonstrates a load-bearing platform pattern (pattern 13, the stream-table join, see `docs/agent-instructions/events/13-stream-table-join.md`) that is otherwise only exercised inside transaction-service's matching topology. The course/project context places explicit value on covering streaming patterns end-to-end.

A third question: if we enrich, should the enriched event be **per-user** (table-table join against user.display-currency) or **broadcast** (one event per tick carrying a map of all supported currencies)?

## Decision

Introduce a Kafka Streams application (scope 03, see `docs/event-processes/03-fx-price-enrichment.md`) that performs the join. It joins:

- `crypto.price.clean` (KStream, keyed by symbol)
- `reference.fx.rate` (GlobalKTable, keyed by currency pair)

and emits `LocalizedPrice` (Avro per ADR-0032) to a new delete-retention topic `crypto.price.localized`, keyed by symbol. Each `LocalizedPrice` carries the source USDT price plus a `prices` map keyed by ISO-4217 currency code covering every currency in the supported set.

The enrichment is **broadcast**, not per-user routed. Portfolio and Trading consume `LocalizedPrice`, materialize the latest per symbol, and at API read time pick `prices[user.displayCurrency]` for the requesting user.

## Consequences

The platform covers pattern 13 in a customer-facing flow rather than only inside the matching topology, which is the educational goal of the scope.

One additional streams application, one additional topic, one additional set of failure modes. The application is stateless except for the GlobalKTable replica, so scaling and reprocessing are straightforward.

Broadcast keeps the join simple (no repartitioning to userId, no fan-out per user), and bounds the payload to O(supportedCurrencies). The supported set is currently four currencies (USD, EUR, CHF, GBP); growth here is linear in payload size and topic throughput.

Adding a new supported currency requires updating fx-rate-service's pair list, the scope-03 currency-set configuration, and consumer expectations. It does not require any per-user backfill.

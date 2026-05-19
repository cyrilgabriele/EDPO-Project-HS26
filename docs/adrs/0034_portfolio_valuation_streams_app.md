# 34. Portfolio valuation as a Kafka Streams app inside portfolio-service

Date: 2026-05-20

## Status

Accepted

## Context

Scope 04 (`docs/event-processes/04-portfolio-valuation.md`) requires
continuously computing per-user portfolio value (sum of `qty × price`) and
exposing it through interactive queries. It must also realise three patterns
the course requires: **Table-Table Join**, **Multiphase Repartitioning** and
**Processing with Local State**.

Three placement questions stood open:

1. *Host* — new `portfolio-valuation-service` module vs inside
   `portfolio-service`.
2. *Holdings source* — replay `transaction.order.approved` vs CDC the
   existing Postgres `holding` table.
3. *Price source* — `crypto.price.raw` (USDT, exists) vs
   `crypto.price.localized` (ADR-0030, not yet built).

A fourth question — processing guarantee — defaulted to `at_least_once`
because all folds are commutative and associative (signed-sum into a
holdings KTable; latest-value table for prices); retries cannot
double-count.

## Decision

- The streams app lives inside `portfolio-service` as a new
  `@Configuration` (`PortfolioValuationStreamConfig`), with its own
  `applicationId` (`portfolio-service-valuation`) so the existing
  `@KafkaListener` consumer group is unaffected.
- Holdings are projected by **replaying `transaction.order.approved`** and
  folding a signed quantity into a KTable keyed by `userId|symbol`. The
  existing Postgres `holding` table stays as a parallel read model
  maintained by the existing `OrderApprovedEventConsumer`.
- The price input is **`crypto.price.raw`** (USDT-denominated). This
  matches the ADR-0028/0031 doctrine of aggregating in USDT and converting
  to Display Currency at API read time. When ADR-0030's
  `crypto.price.localized` ships, this remains the right input for the
  USDT-canonical aggregate; localisation continues to happen on read.
- Processing guarantee: **`at_least_once`** with idempotent folds.
- Two interactive-query surfaces coexist:
  `GET /portfolios/{userId}/value` (existing, Postgres-backed) and
  `GET /portfolios/{userId}/streams-value` (new, state-store-backed). The
  side-by-side comparison is the report deliverable from scope 04.
- Multi-instance metadata-redirect for IQ is **out of scope** — dev runs
  one replica. Documented as a known limitation.

## Consequences

- One topology in portfolio-service realises three of the four required
  patterns in a single user-facing flow (FK Table-Table Join, multiphase
  repartition via `KTable.groupBy(userId)`, and Local State via the
  materialised `portfolio-value-store`).
- Holdings now exist in two places — Postgres (via the existing consumer)
  and the Streams state store. Both are deterministic projections of the
  same `OrderApprovedEvent` topic, so they stay consistent; they cannot
  drift independently because neither writes to the other.
- A fresh `applicationId` triggers an offset-reset rebuild that reads the
  full `transaction.order.approved` and `crypto.price.raw` history from
  the start — that gives us the "reprocessing" demo for free.
- portfolio-service now needs the `kafka-streams` and
  `kafka-streams-avro-serde` dependencies in addition to its existing
  Spring Kafka consumer machinery.
- portfolio-service also needs a runtime image compatible with RocksDB JNI
  and Zeebe's native transport stack. It uses the Debian Temurin JRE with
  `libstdc++6`, matching the existing transaction-service pattern.
- A future sell flow only needs to change `SignedQuantity.of(...)`; the
  rest of the topology is side-agnostic.

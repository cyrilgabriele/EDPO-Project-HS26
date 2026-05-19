# 32. Use Avro + Confluent Schema Registry for derived events

Date: 2026-05-16

## Status

Accepted

## Context

ADR-0003 set JSON as the platform-wide Kafka serialization default. ADR-0022 carved out Avro for derived Market Scout events using a registryless serde, deliberately keeping Schema Registry out of the stack. The new scopes introduced by ADR-0028 through ADR-0031 add more derived events with multiple producers and consumers:

- `FxRate` (fx-rate-service → enrichment + portfolio + trading)
- `LocalizedPrice` (scope-03 streams app → portfolio + trading)
- `PortfolioValue` (portfolio-service → downstream readers / queries)
- `Ohlc` (scope-05 streams app → dashboard read APIs)
- `UserDisplayCurrencyUpdated` (user-service → portfolio + trading)

These contracts cross service ownership boundaries. ADR-0022's registryless approach worked when one service owned both producers and consumers of a derived event; with multiple independent consumers per topic, informal schema evolution becomes a real liability.

## Decision

Adopt **Avro + Confluent Schema Registry** for the five derived event types listed above. Add a `schema-registry` service (Confluent image) to `docker/docker-compose.yml`. Inside the Docker network it is reachable at `http://schema-registry:8081`; from the host it is published on `http://localhost:8090` (host port 8081 was already taken by market-data-service). Producers and consumers use `KafkaAvroSerializer` / `KafkaAvroDeserializer` configured against `schema.registry.url`.

Existing topics keep their current serialization:

- **JSON** (per ADR-0003): `crypto.price.raw`, `transaction.order.approved`, `transaction.buy-bids`, `transaction.order-matched`, `user.confirmed`, `crypto.portfolio.compensation`, `crypto.user.compensation`, `crypto.scout.raw`.
- **Avro, registryless** (per ADR-0022): `crypto.scout.matchable-asks`, `crypto.scout.ask-quotes`, `crypto.scout.ask-opportunities`, `crypto.scout.window-summary`.

Future migration of the registryless Market Scout topics onto the registry is out of scope for this ADR and can be done service-by-service if it becomes useful.

## Consequences

The five new derived events get compile-time contracts and centrally-managed schema evolution rules. Producers cannot accidentally publish a schema-breaking change without the registry rejecting it (subject to configured compatibility level, with `BACKWARD` as the recommended starting point).

ADR-0003's "JSON everywhere" intent is narrowed to its strongest case: JSON remains the format for replay-boundary raw topics, externally-facing identity events, and existing flows that are already debugged and observable in plain text. New cross-service derived contracts use registered Avro.

One additional infrastructure dependency (Schema Registry) in dev and production. The compose file change is small; the runtime cost on a laptop is negligible. Build pipelines for services that use Avro need a step that codegens specific records from `.avsc` schemas under `shared-events/`.

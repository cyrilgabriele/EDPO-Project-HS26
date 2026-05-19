# 01. FX Rate Ingestion

**Type:** stateless &nbsp;|&nbsp; **Required pattern:** Single-Event Processing &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** ready

## Purpose

Poll a public FX API on a timer, filter and translate the response, and publish rates to a log-compacted Kafka topic that scope 3 materializes as a KTable.

## Why this matters

Slow-moving reference data must be cached in Kafka rather than called per event. The compacted topic *is* the cache — it survives restarts, is replayable, and removes the HTTP API from the event path.

## Decisions locked in

- **Host:** new service `fx-rate-service` in a new Reference Data bounded context, see [ADR-0029](../adrs/0029_fx_rate_service_as_reference_data_context.md).
- **Serialization:** Avro + Confluent Schema Registry, see [ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md). Schema Registry is added to `docker/docker-compose`.
- **Cross-context contract:** documented in [00-display-currency-cross-context.md](00-display-currency-cross-context.md); glossary in [docs/fx-rate-service/CONTEXT.md](../fx-rate-service/CONTEXT.md).

## Patterns hit (catalog references)

| File | Role |
|---|---|
| `10-single-event-processing.md` | per-fetch map/filter, no aggregation |
| `25-event-filter.md` | drop malformed / stale rates before publishing |
| `26-event-translator.md` | normalize currency codes and units into canonical form |
| `06-state-and-stream-table-duality.md` | compacted topic materialized as KTable downstream |
| `20-data-contract.md` | `FxRate` Avro schema as the contract with scope 3 |
| `21-event-serializer-deserializer.md` | Avro + schema registry |

## Topology

![FX rate ingestion topology](diagrams/01-fx-rate-ingestion.svg)

<!-- Source: diagrams/01-fx-rate-ingestion.puml — regenerate with `plantuml -tsvg diagrams/01-fx-rate-ingestion.puml` -->

### Control flow

1. **Source** — HTTPS poll against `exchangerate.host/latest` on a 5-minute timer (adjustable). One response per poll carries all tracked currency rates relative to a single base.
2. **Event Filter** — reject responses that are malformed (missing fields, unparseable rates) or older than the configured validity window.
3. **Event Translator** — fan out the accepted response into one `FxRate` event per quote currency. Currency codes normalized to canonical ISO-4217 form, rate units converted if needed, and `fetchedAt` (now) plus `validFromAt` (response timestamp) stamped onto each event.
4. **Sink** — publish each `FxRate` to the log-compacted `reference.fx.rate` topic, keyed by the currency pair (e.g. `USDCHF`). Compaction collapses repeat rates for the same pair into the latest value, so the topic always holds the current view of all pairs.

## Inputs

| Source | Type | Notes |
|---|---|---|
| `exchangerate.host/latest` | HTTPS JSON | free, no API key |

Fallback candidates: ECB reference rates (XML, daily), Fixer.io (paid).

## Outputs

| Topic | Cleanup | Key | Value | Partitions |
|---|---|---|---|---|
| `reference.fx.rate` | compact | `"USDCHF"` string | `FxRate` (Avro) | 1 |

### Avro — `ch.unisg.cryptoflow.shared.events.fx.FxRate`

```
base:        string                   // "USD"
quote:       string                   // "CHF"
rate:        double                   // quote per 1 base
source:      string                   // "exchangerate.host"
fetchedAt:   timestamp-millis
validFromAt: timestamp-millis
```

## State stores

None. The compacted topic is the state — scope 3 owns the KTable materialization.

## Joins / Windowing

N/A.

## Processing guarantees

- At-least-once publish.
- Idempotent by key: log compaction collapses repeat rates into the latest value per pair.

## Interactive queries

N/A at this scope. Optional passthrough on scope 3.

## Open decisions

- [x] ~~Host FX fetcher as its own service, or extend market-data-service?~~ → **new `fx-rate-service`** ([ADR-0029](../adrs/0029_fx_rate_service_as_reference_data_context.md)).
- [x] ~~Registry-aware vs registryless Avro.~~ → **registry-aware** ([ADR-0032](../adrs/0032_avro_schema_registry_for_derived_events.md)); add `schema-registry` to `docker-compose`.
- [x] ~~Which currency pairs.~~ → **USD anchor, supported set {USD, EUR, CHF, GBP}** (see cross-context spec).
- [x] ~~Poll interval.~~ → **5 minutes** as the operational default.
- [ ] exchangerate.host vs ECB vs Fixer.io. Operational; default `exchangerate.host` with ECB as documented fallback.
- [ ] Tombstone on invalid response, or skip silently? Propose **skip silently**, log + metric; tombstones are reserved for "this pair is being removed from the supported set".

## ADR candidates

All originally-listed ADR candidates are subsumed:
- ~~ADR for FX data-source selection.~~ → not material; provider is an operational choice behind the same Avro contract.
- ~~ADR for FX topic schema and key shape.~~ → string-pair key (e.g. `"USDCHF"`); schema fixed in `shared-events/src/main/avro/FxRate.avsc` and pinned in [00-display-currency-cross-context.md](00-display-currency-cross-context.md).

## Related scopes

- Consumer: `03-fx-price-enrichment.md`.

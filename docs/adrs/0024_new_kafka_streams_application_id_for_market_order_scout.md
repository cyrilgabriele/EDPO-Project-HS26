# 24. Use a new Kafka Streams application id for Market Order Scout after the split

Date: 2026-05-03

## Status

Accepted

## Context

ADR-0023 split the previous combined Market Scout service into `market-partial-book-ingestion-service` and `market-order-scout-service`. The downstream service still runs the same Kafka Streams topology over `crypto.scout.raw`, but the Spring application name changed to `market-order-scout-service`.

The Kafka Streams configuration derives `application.id` from the Spring application name by appending `-topology`. Renaming the service therefore changes the Streams application id from the old combined service id to `market-order-scout-service-topology`.

Kafka Streams application ids define consumer group membership, committed offsets, internal changelog/repartition topic names, and local state directories. Reusing the old id would preserve existing offsets and state, but it would keep operational coupling to the pre-split service identity.

## Decision

Accept the new Kafka Streams application id:

```text
market-order-scout-service-topology
```

Do not preserve or migrate the old local Kafka Streams state for this project context. The new service may reprocess `crypto.scout.raw` from the configured `earliest` offset policy when no committed offsets exist for the new application id.

The raw topic remains replayable and keyed by symbol, so rebuilding derived scout topics from raw events is the intended recovery path.

## Consequences

The split has a clean operational identity: the ingestion service owns raw capture, and the scout service owns a distinct Kafka Streams application.

Existing local state and offsets from the previous combined service are disposable. On a reused Kafka cluster, the new application id can replay old raw events and emit derived records again. This is acceptable for the course project and local development environment because the derived scout topics are demonstration outputs, not externally committed financial decisions.

If the Market Scout topology later becomes production-facing, the project should introduce an explicit migration or reset procedure for Kafka Streams offsets, state directories, and derived topics before changing application ids.

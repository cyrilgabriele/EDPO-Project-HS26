# ADR-0014: Outbox Pattern for Order Approval

**Date**: 2026-03-30

## Status

Accepted

## Context

Approving an order requires two operations that must succeed or fail together:

1. Persist the APPROVED status in the transaction database.
2. Publish an `OrderApprovedEvent` to Kafka so `portfolio-service` can update holdings.

A crash or broker outage between steps 1 and 2 leaves the transaction marked APPROVED while the portfolio is never updated — a silent data integrity defect with no recovery path.

## Decision

Use the **Transactional Outbox** pattern. The approval step writes both the status change and the event payload into the same local DB transaction. A separate publication step reads the outbox and publishes to Kafka, marking the row as published on success.

A scheduled safety net runs periodically and republishes any outbox rows that remain unpublished beyond a threshold (indicating a crash between the two steps). This decouples crash recovery from the BPMN flow — the orchestrator does not need to model this failure.

Downstream consumers must handle at-least-once delivery. See ADR-0016.

## Consequences

- **Positive**: Status change and event publication are atomically durable. No event is silently lost across crashes or broker failures.
- **Negative**: Adds an `outbox_events` table and a background scheduler. Publication is no longer a single inline call — it requires monitoring for stale unpublished rows.
- **Watch**: The scheduler introduces a small publication lag on crash recovery. Any consumer of this event must be designed for at-least-once semantics.

## Alternatives Considered

**Direct Kafka publish (inline)**
Publish to Kafka within the same worker call, after the DB write. Simple, but if the broker is unavailable or the JVM crashes after the DB commit, the event is permanently lost. Rejected — no recovery path.

**Change Data Capture (CDC)**
A CDC connector (e.g., Debezium) streams DB changes to Kafka, removing the need for an explicit outbox table. Stronger guarantees and no scheduler required. Rejected for now — adds operational complexity (connector cluster, schema registry alignment) disproportionate to current scale. Valid future migration path.

## References

- ADR-0013 — saga context
- ADR-0015 — portfolio durability (downstream of this guarantee)
- ADR-0016 — idempotent consumer

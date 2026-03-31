# ADR-0015: Portfolio Update Durability for Approved Orders

**Date**: 2026-03-30

## Status

Accepted

## Context

After `approveOrderWorker` persists the approval atomically with an outbox row (ADR-0014), `publishOrderApprovedWorker` reads the outbox row and publishes `OrderApprovedEvent` to Kafka. `portfolio-service` independently consumes this event and calls `upsertHolding()`. The question is: should the BPMN process wait for portfolio confirmation before sending the executed email, or should email fire immediately after the event is published?

## Decision

The executed email fires immediately after `publishOrderApprovedWorker` completes. Portfolio update is pure **Event-Carried State Transfer (ECST)**: `portfolio-service` consumes `OrderApprovedEvent` from Kafka and updates holdings autonomously, with no acknowledgement back to the orchestrator.

```
approveOrderWorker
    → publishOrderApprovedWorker  (publishes Kafka event, marks outbox published)
    → Send Executed Email → End
```

Portfolio-service owns its own ACID boundary for `upsertHolding()`. Kafka consumer retry and a Dead Letter Topic handle transient and persistent failures within that boundary. The overall process remains a pure **Fairy Tale Saga (seo)** — Zeebe orchestrates service tasks synchronously; cross-service consistency is eventual.

The executed email confirms the order execution (the business event), not portfolio propagation. These are semantically distinct: the order is approved the moment `transaction_record` is set to APPROVED. Portfolio propagation is a downstream consequence that is guaranteed to eventually succeed via Kafka durability and the outbox safety net.

## Consequences

**Positive**
- Process instances complete immediately after event publication — no open instances accumulating at a catch event.
- Portfolio-service is fully self-contained: it owns its retry logic, DLT handling, and failure visibility without coupling to the Zeebe instance lifecycle.
- Email latency is minimal and deterministic — not gated on `portfolio-service` availability.
- No additional shared-events types, Kafka topics, or BPMN message correlations required.
- Aligns with the Fairy Tale Saga pattern throughout: eventual consistency is explicitly accepted at cross-service boundaries.

**Negative**
- Portfolio failures are not visible in Camunda Operate; monitoring requires separate alerting on the DLT or portfolio-service logs.
- A user could receive the executed email before their portfolio reflects the new holding (brief eventual consistency window). Acceptable for the current read-only portfolio display use case.
- If `portfolio-service` is persistently down, events accumulate in the DLT rather than surfacing as Zeebe incidents. Operator runbooks for DLT re-processing must be maintained.

## Alternatives Considered

**Intermediate Message Catch Event (previously implemented, then rejected)**
Extend the BPMN approval path with a catch event awaiting a `PortfolioUpdatedAckEvent` reply. `portfolio-service` publishes the ack after `upsertHolding()` succeeds; `transaction-service` correlates the message to resume the process and then sends the email.

- Rejected because: it contradicts the Fairy Tale Saga pattern — waiting for a downstream consumer ack within an orchestrated flow introduces unnecessary coupling and transforms the saga toward an Epic pattern. Process instances remain open indefinitely if `portfolio-service` is down, requiring boundary timers and escalation runbooks. The added complexity (new event type, new topic, BPMN correlation config, ack consumer) is not justified for a consequence-only update where the order business event has already been committed.

## References

- ADR-0013 — Fairy Tale Saga (seo) for placeOrder
- ADR-0014 — Outbox pattern (prerequisite durability guarantee)
- `portfolio-service/src/main/java/…/adapter/in/kafka/OrderApprovedEventConsumer.java`
- `transaction-service/src/main/resources/placeOrder.bpmn`
- *Software Architecture: The Hard Parts*, Ch. 12 — Fairy Tale Saga (Table 12-4)
- *Practical Process Automation*, Ch. 9 — Asynchronous Messaging Patterns

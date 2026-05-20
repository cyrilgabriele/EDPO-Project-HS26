# ADR-0035: Loop Ops Resolution Back into the placeOrder Happy Path

**Date**: 2026-05-20

## Status

Accepted. Partially supersedes [ADR-0018](0018_human_escalation_for_approval_and_publication_failures.md) —
the human-escalation pattern is retained (typed BPMN error → ops user task in Tasklist with a mandatory
resolution note), but the post-task routing changes from "terminate the process" to "re-enter the happy
path."

## Context

ADR-0018 routed both deterministic failure cases — `TRANSACTION_NOT_FOUND` (approve step) and
`OUTBOX_EVENT_NOT_FOUND` (publish step) — to ops user tasks that ended at terminal "Resolved" end events.
This surfaced the failure in Tasklist with full context, but it had a discovered cost: closing an ops
task terminated the process instance, so the user-visible closure email never fired, and the publish-
failure case ended with the Kafka event already published (`PublishOrderApprovedWorker.java:62`
publishes before the outbox lookup) — `portfolio-service` was updated via the idempotent consumer, but
the transaction process model had no record of completion. Both outcomes leave the workflow in a state
where ops acknowledged the failure but the system never finished the order on the user's behalf.

ADR-0018 considered routing the ops task back to the failed step as a future improvement and deferred
it on the grounds that the form cannot enforce that ops actually fixed the root cause before completing
the task. That concern is real but it produces a self-healing loop rather than corruption: if ops closes
without fixing, the same boundary error fires on retry and the ops task reappears in Tasklist.

The two failure cases also differ in what has happened by the time the boundary fires:

- `TRANSACTION_NOT_FOUND` — `approveWithOutbox` is atomic; the exception is raised before any DB write.
  Status remains PENDING, no outbox row, no Kafka event. Nothing is done.
- `OUTBOX_EVENT_NOT_FOUND` — Kafka publish has already executed (`PublishOrderApprovedWorker.java:62`)
  before the outbox-row lookup that triggers the error. `portfolio-service` will update holdings via
  the idempotent consumer (ADR-0016). Only the outbox bookkeeping and the user-facing email are
  unfinished.

A single uniform "retry the failed step" rule does not fit both cases: retrying the publish step would
require ops to recreate the missing outbox row purely so the BPMN can mark it published — busywork that
does not correspond to any real-world remediation, because the Kafka publish has already happened.

## Decision

After ops completes an intervention task, the process re-enters the happy path rather than terminating.
The routing is asymmetric to match the asymmetric semantics of the two errors:

- `Activity_ops_approve_error` → loops back to `Activity_order_approved` (Approve Order). Ops's
  remediation is to restore the missing transaction record; the retry then runs the atomic
  approve-with-outbox write and the flow continues to `Notify Portfolio` → email → end.
- `Activity_ops_publish_error` → continues forward to `Activity_0p6fmth` (Send Order Executed Email),
  skipping the publish step. Ops's remediation is recording the audit trail for the outbox-row anomaly
  out of band; the user-facing email still fires because the order business event has already been
  committed (status APPROVED, Kafka published).

The terminal "Resolved" end events (`Event_ops_approve_end`, `Event_ops_publish_end`) and the
sequence flows leading to them are removed.

No retry counter, escalation timer, or "abort" branch is modelled. If a pathological case requires
giving up on an order, the operator cancels the process instance in Camunda Operate — the standard
operational escape hatch.

## Consequences

**Positive**
- The process always reaches a terminal end event on the happy path. No silent termination on a
  deterministic failure; the executed email always fires once the order is actually executed.
- Ops behaviour is self-healing rather than enforced: a premature close re-raises the same task, which
  is operationally visible and bounded by human task-cycle time rather than tight worker-retry loops.
- The asymmetric routing reflects the real difference between "work not done yet" (approve) and
  "work done, bookkeeping anomaly" (publish), avoiding ops busywork.

**Negative**
- The BPMN now contains two structurally different ops paths — a future contributor must read this ADR
  to understand why approve retries and publish skips forward.
- ADR-0018's "unenforceable" concern technically still holds. The mitigation is that the cost of an
  unenforced close is now "ops task reappears" rather than "data integrity defect persists silently."

**Watch**
- If `OUTBOX_EVENT_NOT_FOUND` ever fires in production, the missing outbox row is now a permanent
  anomaly: the Kafka event was published but no row tracks it. The ops resolution note is the audit
  trail. Out-of-band outbox reconciliation tooling remains a future concern.

## Alternatives Considered

**Status quo (ADR-0018 unchanged)** — keep terminal end events.
Rejected because the discovered failure mode (silent termination with no closure email, and in the
publish case no follow-through despite Kafka already publishing) outweighs the original enforceability
concern.

**Symmetric retry (both ops tasks loop back to their failed activity)**
Conceptually cleaner. Rejected because for `publish_error` it forces ops to recreate the outbox row
purely for BPMN-side bookkeeping, even though the actual remediation work has nothing to do with that
row's existence.

**Symmetric skip-forward (both ops tasks continue to Send Email)**
Wrong for `approve_error` — the order is not actually approved when that boundary fires, so the email
would announce an execution that never happened. Rejected on correctness grounds.

**Form-driven Retry/Skip/Abort gateway**
Adds a `resolutionAction` form field and an exclusive gateway, modelling ops intent explicitly.
Deferred — the chosen asymmetric routing covers the known failure modes, and "abort" can be handled by
cancelling the instance in Operate. Worth revisiting if the failure modes broaden.

**Bounded retry counter with terminal "Order Abandoned" end event**
Adds a script task and instance variable to track retry count. Rejected as premature — the failure
mode it guards against (ops repeatedly closing the task without fixing the cause) has not been
observed and would not be silently worse than today's behaviour, because the task continues to surface
in Tasklist on every loop.

## References

- ADR-0013 — Fairy Tale Saga (seo) for placeOrder
- ADR-0014 — Outbox pattern (origin of the outbox row)
- ADR-0015 — Portfolio Update Durability for Approved Orders
- ADR-0016 — Idempotent consumer for portfolio updates (makes a publish-step re-run safe even if a
  future change reintroduces it)
- ADR-0018 — Human Escalation for Deterministic Workflow Failures (partially superseded)
- `transaction-service/src/main/resources/placeOrder.bpmn`
- `transaction-service/src/main/java/.../ApproveOrderWorker.java`
- `transaction-service/src/main/java/.../PublishOrderApprovedWorker.java`

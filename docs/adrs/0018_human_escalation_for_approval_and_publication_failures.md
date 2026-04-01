# ADR-0018: Human Escalation for Deterministic Workflow Failures

**Date**: 2026-03-31

## Status

Accepted

## Context

Zeebe's built-in retry and incident mechanism handles transient infrastructure failures (broker down, DB timeout) well. However, the `placeOrder` workflow contains failure cases that are **deterministic and non-retryable**: a record that does not exist will not appear on the next retry. Retrying indefinitely wastes resources and delays visibility.

Two such cases exist in the approval flow:
- The transaction record is missing when the approval step executes.
- The outbox row written by the approval step is missing when the publication step executes.

Both indicate data integrity problems that require human investigation.

## Decision

For each deterministic failure, the worker throws a typed BPMN error. An interrupting boundary event catches it and routes the process to an **ops user task** in Camunda Tasklist, assigned to the operations team. The task exposes all relevant order context and requires a resolution note before it can be completed. The process terminates normally after the operator closes the task.

This is distinct from Zeebe incidents (which appear in Camunda Operate and are invisible in Tasklist). The ops task is chosen because the failure is known, named, and has defined context — it warrants a structured handoff, not a raw incident view.

## Consequences

**Positive**
- Failures surface immediately in Tasklist with full order context rather than requiring operators to search through incident history.
- The mandatory resolution note creates an audit trail per failure.
- The process instance remains open until acknowledged, preventing silent data loss.

**Negative**
- Operators must monitor Tasklist in addition to Operate.
- There is no retry path from the ops task — the operator must fix the root cause outside the process and then acknowledge. If systemic failures occur, the task queue accumulates.

## Alternatives Considered

**Error end event (previous approach)**
Terminates the process immediately. No operator visibility in Tasklist; the instance disappears from active processes. Rejected — too passive for a data integrity failure.

**Automatic retry with backoff**
Keep retrying until the condition resolves. Appropriate for infrastructure failures, not for deterministic ones. A missing DB record will not reappear without intervention. Rejected for these specific failure cases.

**Retry from ops task (loop-back)**
After the operator resolves the root cause, route the process back to the failed step to retry automatically. More powerful but unenforceable — the form cannot guarantee the fix is in place before the task is completed. Deferred as a future improvement.

## References

- ADR-0013 — error boundary context within the saga
- ADR-0014 — outbox pattern (context for the publication failure case)

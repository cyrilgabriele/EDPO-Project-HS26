# CryptoFlow BPMN Workflows

This document describes the BPMN processes deployed to Camunda 8. Each section covers why
orchestration was chosen for the given workflow, how the process operates, and how it can
evolve.

---

## 1. User Onboarding

**Process ID:** `Process_10xcujt` (userCreation.bpmn)
**Deployed by:** `user-service`

### Why orchestration

User registration spans an unbounded waiting period between email dispatch and the user
clicking the confirmation link. A choreography-based approach would require each step
(form handling, email dispatch, confirmation reception, persistence) to communicate via
Kafka events, with no central view of where a given registration stands. Timeout handling
would need a separate scheduled job polling for stale registrations.

With an orchestrated BPMN process, the waiting period is modelled as an intermediate message
catch event. Zeebe owns the process state, handles timeouts natively, and Camunda Operate
provides visibility into every pending registration without custom tooling.

### Process description

```
Start --> [Enter User Credentials] --> [Generate Confirmation Link] --> [Send Confirmation Mail]
      --> (Wait for UserConfirmed message) --> [Create User] --> End
```

1. **Start event** — process instance is created (via Camunda Tasklist or API).
2. **Enter User Credentials** (user task) — a Camunda form (`UserCreation`) collects
   `username`, `password`, and `e_mail`.
3. **Generate Confirmation Link** (service task: `userPreparationWorker`) — generates a UUID
   (`userId`), constructs the confirmation URL from `USER_CONFIRMATION_BASE_URL`, and stores
   the email body in the `userCreationMailContent` process variable.
4. **Send Confirmation Mail** (Camunda email connector: `io.camunda:email:1`) — dispatches
   the confirmation email via SMTP using the process variables set in the previous step.
5. **Wait for Confirmation** (intermediate message catch event) — the process suspends until
   a `UserConfirmed` message with correlation key `userId` is received. This message is
   published by the `GET /user/confirm/{userId}` endpoint when the user clicks the link.
6. **Create User** (service task: `userCreationWorker`) — persists the User aggregate to
   PostgreSQL using the process variables (`userId`, `username`, `password`, `e_mail`).
7. **End event** — process completes.

### Future evolution

- **Timeout boundary event.** Add a non-interrupting timer boundary event on the message
  catch event (e.g. 24 hours). On expiry, send a reminder email. After a second timeout,
  cancel the process and log the abandoned registration.
- **Duplicate detection.** Add an exclusive gateway before the user task that checks whether
  the email address is already registered. Reject duplicates early rather than after the
  full confirmation round-trip.
- **Welcome notification.** After the Create User task, add a parallel branch that publishes
  a `UserCreated` event to Kafka so that other services (e.g. portfolio-service) can react
  to new users.

---

## 2. Place Order

**Process ID:** deployed via Camunda Web Modeler (no `.bpmn` file in source)
**Workers provided by:** `transaction-service`

### Why orchestration

An order placement workflow must: accept an order, wait for a market price match on the
Kafka price stream, and then execute the order. The wait for a matching price is open-ended
(seconds to hours depending on market conditions) and must correlate a specific Kafka price
event to a specific pending order.

A pure choreography approach would scatter this state across the transaction-service's
in-memory map and Kafka offsets, with no central view of which orders are waiting, which
matched, or which timed out. Compensation (e.g. cancelling an unmatched order) would require
custom logic in the service itself.

With Camunda 8, the waiting phase is modelled using an event-based gateway that races two
competing events: a price-match message and a timeout timer. The transaction-service's Kafka
consumer operates independently of the BPMN process — it monitors all incoming price events
and publishes a Zeebe message only when a match occurs. This cleanly separates the
event-driven matching logic (Kafka) from the workflow coordination (Zeebe), while the
gateway provides built-in timeout handling without custom application code.

### Process description

The process uses four swim lanes to separate concerns:

![Place Order Process](img/placeOrder.png)

1. **Start event** (User lane) — process instance is created.
2. **Fill-in Order Details** (User lane, user task) — a Camunda form collects `symbol`,
   `amount`, and `price` (the target price the user is willing to buy at).
3. **Place Order** (OrderProcessingWorker lane, service task: `order-processing-worker`) —
   `OrderProcessingWorker` generates a `transactionId` (UUID) and registers a `PendingOrder`
   in the `OrderMatchingService`'s in-memory `ConcurrentHashMap`. The `transactionId` is
   written back as a process variable for downstream correlation.
4. **Event-based gateway** (OrderProcessingWorker lane) — the process reaches a race
   condition between two competing events:

   **Path A — Price match (happy path):**
   The process waits for a `price-matched` message (PriceMatchingService lane) correlated
   by `transactionId`. Meanwhile, outside the BPMN, the `PriceEventConsumer` continuously
   consumes `CryptoPriceUpdatedEvent` messages from the `crypto.price.raw` Kafka topic. For
   each event, it checks all pending orders: if the current price is at or below the target
   price for the same symbol, the order is matched. `OrderExecutedMessageSender` publishes
   a `price-matched` Zeebe message carrying `matchedPrice`, `matchedSymbol`, and
   `priceMatched`. The process then moves to the Notification lane where **Send Order
   Executed Email** (Camunda email connector) notifies the user of the successful execution.

   **Path B — Timeout (rejection path):**
   A 1-minute timer intermediate catch event fires if no price match is received within the
   order execution window. The process moves to **Send Order Rejected Email** (Camunda email
   connector) which notifies the user that the order could not be filled in time.

5. **End event** — both paths terminate the process instance.

### Design note: in-memory order state

Pending orders are held in a `ConcurrentHashMap` inside `OrderMatchingService`, not in a
database. This avoids persistence overhead for what is a short-lived, high-throughput matching
operation. The consequence is that pending orders are lost if the service restarts. For the
current scope this is acceptable — Camunda Operate surfaces any process instance still
waiting on a `price-matched` message, and the order can be resubmitted. The 1-minute timeout
also bounds the maximum duration of in-memory state.

### Future evolution

- **Persistent order state.** Replace the in-memory map with a database-backed store to
  survive service restarts. The `OrderMatchingService` interface remains unchanged.
- **Configurable execution window.** Expose the timeout duration as a process variable so
  users can choose their own execution window per order rather than a fixed 1 minute.
- **Portfolio integration.** After the price match, add a service task that publishes an
  `OrderExecuted` event to Kafka. Portfolio-service consumes this event and updates the
  user's holdings — closing the loop between order placement and portfolio state.
- **Partial fills and limit orders.** Extend the matching logic to support partial quantity
  fills and differentiate between market orders, limit orders, and stop-loss orders.
- **Multi-symbol orders.** Use a BPMN parallel gateway to wait for price matches across
  multiple symbols in a single order workflow.

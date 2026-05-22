# CryptoFlow — EDPO Presentation Outline

> **Format:** ~20 min total (15 min presentation + 2 min demo + 3 min Q&A)
> **Presenters:** 2 team members, each presenting their sections
> **Focus:** Process-oriented architecture (primary), event-driven architecture (high-level)

---

## Slide 1: Title Slide

- **Title:** CryptoFlow — Event-Driven & Process-Oriented Crypto Portfolio Platform
- **Subtitle:** EDPO Project FS26
- **Team members:** [names]
- **Date:** [presentation date]

---

## Slide 2: What is CryptoFlow?

- Crypto portfolio simulation platform — not a production exchange
- Users register, observe live market prices, manage portfolios, place simulated trades
- Case study for applying EDPO lecture concepts in one coherent end-to-end system
- **Speaker notes:** Keep this brief. The point is that we chose a domain rich enough to require multiple bounded contexts, long-running processes, and eventual consistency — all things we wanted to demonstrate.

---

## Slide 3: Domain — Five Bounded Contexts

- Show the DDD context map (figures/context-map.jpeg)
- Market Data (upstream price feed), Portfolio (holdings & valuation), Trading (order lifecycle), User Identity (accounts & confirmation), Onboarding (saga orchestrator)
- Key relationships: upstream/downstream, partnership (User ↔ Portfolio for compensation), shared kernel (shared-events module)
- **Speaker notes:** Emphasize that the bounded contexts drove the service decomposition. Each context is owned by exactly one service. Cross-context communication is always asynchronous.

---

## Slide 4: Architecture Overview

- Show the deployment overview diagram (figures/deployment-overview.svg)
- 5 Spring Boot microservices inside Docker Compose
- Two communication channels: Kafka (domain events) and Zeebe (process orchestration)
- No synchronous inter-service calls for domain operations — REST only for external clients
- Database-per-service: 3 PostgreSQL databases (User, Portfolio, Transaction); Market Data and Onboarding are stateless
- **Speaker notes:** This is the 30-second mental model. Two channels, five services, no shared databases. Everything else in the presentation builds on this.

---

## Slide 5: Event-Driven Communication (High Level)

- Kafka as the sole inter-service event bus (ADR-0001)
- 6 topics: price ticks, approved orders, user confirmations, compensation (×2), dead-letter
- Event-Carried State Transfer in 4 places: price cache, portfolio updates, compensation events, replicated user validation
- Key config: `acks=all`, `auto.offset.reset=earliest`, manual offset commit, DLT after 3 attempts
- **Speaker notes:** We won't deep-dive into event-driven architecture here — that's the focus of the next presentation. The key takeaway: services communicate through published facts, not remote commands. ECST lets each service maintain its own local view without querying others.

---

## Slide 6: Kafka Experiments — Assignment 1 (Summary)

- Four experiments on multi-broker clusters validating Kafka reliability guarantees
- **acks=0:** eventID=76 permanently lost — producer cannot detect the loss
- **acks=1:** eventID=71 ACKed by leader but lost when leader crashed before replication
- **acks=all:** ~8.9s availability gap during failover, but zero data loss
- **Offset reset:** `earliest` replays retained history; `latest` silently skips it
- These findings directly informed our production config choices (ADR-0001)
- **Speaker notes:** Show the producer/consumer log snippets briefly. The main insight: durability is not a Kafka default — it requires explicit configuration. Our single-broker dev setup means `acks=all` is equivalent to `acks=1` locally; we accept this as a conscious dev-environment trade-off.

---

## Slide 7: Why Camunda 8 / Zeebe?

- Transition slide: "From events to processes"
- Two workflows need durable waiting states: onboarding (email confirmation) and order placement (price match)
- Why not Camunda 7 / Operaton?
  - Embedded engine stores process state in the application DB — clutters domain schema
  - Many concurrent `placeOrder` instances waiting for price matches would need a shared polling table
  - Kafka and SMTP steps require custom application-level client code
- Camunda 8 / Zeebe advantages for CryptoFlow:
  - Partitioned log: each instance waits independently, no job-lock contention
  - Connector templates: Kafka + SMTP inside BPMN, zero notification code in the application
  - Camunda Operate: runtime visibility into all instances without custom dashboards
- **Speaker notes:** This is not a generic "Camunda 8 is better" argument. These are project-specific reasons. The partitioned log matters because placeOrder has a non-deterministic wait — many orders can be open simultaneously. The connector templates matter because our flows include email notifications.

---

## Slide 8: Two Saga Patterns — Driven by Wait Semantics

- CryptoFlow implements two different orchestrated saga styles
- The choice was driven by the **nature of the wait**, not by preference
- **Onboarding → Parallel Saga (aeo):** Waits for a deterministic human action (clicking a confirmation link). Spans two bounded contexts (User + Portfolio). Needs all-or-nothing consistency.
- **Order Placement → Fairy Tale Saga (seo):** Waits for a non-deterministic market event (price match). Stays inside one bounded context (Trading). Accepts eventual consistency with Portfolio.
- **Speaker notes:** This was one of the most instructive decisions. The wait semantics dictated the coupling model, which dictated the saga style. A deterministic wait with cross-context scope → Parallel. A non-deterministic wait within one context → Fairy Tale.

---

## Slide 9: Onboarding Process — Parallel Saga

- Show the BPMN diagram (figures/userOnboarding-bpmn.png)
- Brief walkthrough of the happy path:
  1. User fills form → prepareUserWorker generates userId, creates PENDING confirmation link
  2. Camunda email connector sends confirmation email
  3. Event-based gateway: confirmation click vs. 1-min timeout
  4. Parallel gateway fans out to userCreationWorker + portfolioCreationWorker
  5. Both must succeed → process completes
- **Speaker notes:** Point to the BPMN diagram as you walk through. Emphasize: three services participate but only one (onboarding-service) owns the BPMN. The workers are stateless — they execute local transactions and report back.

---

## Slide 10: Onboarding — Compensation & Flag-Driven Completion

- The critical design challenge: what if one creation succeeds and the other fails?
- **Flag-driven completion (ADR-0012):** Workers always complete their Zeebe jobs, communicating outcome via `isUserCreated` / `isPortfolioCreated` flags
  - Why not throw BPMN errors? That would short-circuit the flow and bypass the modeled compensation gateways
- **Compensation (ADR-0011):** Exclusive gateways inspect the flags → compensation handlers delete the entity + publish event-carried compensation request for the peer service
  - `UserCompensationRequestedEvent` / `PortfolioCompensationRequestedEvent` carry all data needed for remote delete
  - Idempotent deletes — safe under at-least-once delivery
- **Speaker notes:** This is where theory meets implementation. The flag-driven pattern is subtle but essential — without it, the BPMN gateway branches become unreachable. The compensation events use ECST so no synchronous callback is needed.

---

## Slide 11: Dedicated Onboarding Service (ADR-0010)

- Originally, the BPMN lived inside user-service
- Problem: the orchestrator now coordinates work across two bounded contexts → coupling + mixed responsibilities
- Solution: extract into a dedicated onboarding-service
  - Owns the BPMN process and Zeebe deployment
  - No persistent business data — workflow state lives in Zeebe
  - User-service and portfolio-service keep their domain autonomy
- Trade-off: one more deployable service (5 instead of 4), but clean separation of orchestration from domain logic
- **Speaker notes:** This was an ADR-driven decision mid-project. The original design violated bounded context boundaries. The dedicated service is the most important structural consequence of applying the saga pattern correctly.

---

## Slide 12: Place Order Process — Fairy Tale Saga

- Show the BPMN diagram (figures/placeOrder-bpmn.png)
- Brief walkthrough:
  1. User submits order → order-processing-worker validates user (local read-model), creates PENDING transaction
  2. Event-based gateway: price match (correlated by transactionId) vs. 1-min timeout
  3. On match: approveOrderWorker writes APPROVED + outbox row in one DB transaction
  4. publishOrderApprovedWorker publishes outbox to Kafka → email notification
  5. Portfolio update happens independently via Kafka (not part of the orchestrated flow)
- **Speaker notes:** Key distinction from onboarding: the approval is terminal in the trading context. Portfolio propagation is a downstream consequence, not a saga step. This is exactly the Fairy Tale Saga trade-off: synchronous within the context, eventual across contexts.

---

## Slide 13: Reliability Patterns in the Trading Flow

- Three patterns protect the order execution path:
- **Transactional Outbox (ADR-0014):** Approval status + outbox row in same DB transaction. Prevents silent event loss on crash between commit and Kafka publish. Scheduler republishes stale rows.
- **Idempotent Consumer (ADR-0016):** `processed_transaction` table with UNIQUE constraint on transactionId. Prevents duplicate portfolio updates on Kafka redelivery. We found this bug during integration — 2 BTC order → 4 BTC in portfolio.
- **Human Escalation (ADR-0018):** Deterministic failures (missing records) throw typed BPMN errors → boundary events route to ops user task in Tasklist. No endless retries for problems that need investigation.
- **Speaker notes:** These three patterns form a chain: the outbox guarantees the event is published, the idempotent consumer guarantees it's applied exactly once, and human escalation handles the cases that automated retries cannot fix. The idempotency bug was discovered accidentally during integration — a real validation of the pattern's necessity.

---

## Slide 14: Replicated Read-Model for Autonomous Validation

- Problem: transaction-service must reject orders from unconfirmed users, but calling user-service synchronously violates service autonomy
- Solution (ADR-0017): transaction-service maintains its own table of confirmed users
  - user-service publishes `UserConfirmedEvent` to a log-compacted Kafka topic
  - transaction-service consumes and upserts into local DB
  - Order validation = local read, sub-millisecond, no network call
- Trade-off: brief eventual consistency window (acceptable — a newly confirmed user won't place an order immediately)
- **Speaker notes:** This is a focused use of CQRS: the write model stays in user-service, the trading context keeps only the projection it needs. Log compaction ensures the read-model survives cold starts.

---

## Slide 15: Challenges & Lessons Learned

- **BPMN learning curve:** Modeling gateways, timers, compensation, and message correlations was initially demanding — but once mastered, it reduced implementation effort significantly
- **Concepts intuitive in theory, complex in practice:** Orchestration, compensation, ECST feel simple on paper — implementation multiplies the decisions (event structure, correlation keys, error handling, service boundaries)
- **Saga pattern selection:** The distinction between Parallel and Fairy Tale only became clear by analyzing the nature of the wait — deterministic vs. non-deterministic
- **Integration issues surface late:** Most bugs appeared at integration time (deserialization mismatches, wrong correlation keys, missing compensation fields)
- **Two people, five services:** Ambitious scope — ADR-0010 was architecturally right but added overhead
- **Speaker notes:** Be honest about the challenges. The key message: the patterns work, but applying them requires many more decisions than the theory suggests. ADRs were essential for keeping those decisions traceable.

---

## Slide 16: Live Demo

- **Demo flow 1 — Happy-path onboarding:**
  - Start process in Tasklist → fill form → receive confirmation email → click link → user + portfolio created → show in Operate
- **Demo flow 2 — Happy-path order placement:**
  - Place order in Tasklist → wait for price match → order approved → portfolio updated → show notification email
- **Optional if time permits:**
  - Show a timeout scenario (order rejected after 1 min)
  - Show Kafka UI: topics, consumer groups, message payloads
  - Show Camunda Operate: process instances, variables, history
- **Speaker notes:** Keep the demo tight — 2-3 minutes max. Have the Docker Compose stack running before the presentation starts. If something breaks live, switch to Operate to show the instance state — that's still a valid demo of observability.

---

## Slide 17: Summary & Key Takeaways

- CryptoFlow: 5 microservices, 2 communication channels (Kafka + Zeebe), 11 EDPO lecture concepts implemented
- Process-oriented highlights:
  - Two saga patterns chosen by wait semantics, not by preference
  - Flag-driven completion keeps compensation paths reachable
  - Transactional outbox + idempotent consumer + human escalation as a reliability chain
  - Dedicated orchestration service to preserve bounded context autonomy
- 20 ADRs documenting every architectural decision with rationale and trade-offs
- **Speaker notes:** This is the wrap-up. Reiterate the most distinctive architectural choices. Then open for Q&A.

---

## Slide 18: Q&A

- "Questions?"
- Optionally: prepare backup slides for anticipated questions:
  - Why not choreography instead of orchestration?
  - Why JSON over Avro for Kafka serialization?
  - How would you scale this to production?
  - What would you do differently next time?

---

## Presenter Split Suggestion

| Section | Presenter | Slides | ~Time |
|---------|-----------|--------|-------|
| Title, CryptoFlow intro, Domain, Architecture overview | Presenter A | 1–4 | 3 min |
| Event-driven (high level), Kafka experiments | Presenter A | 5–6 | 2 min |
| Camunda 8, Saga patterns, Onboarding process + compensation, Dedicated onboarding service | Presenter B | 7–11 | 6 min |
| Place order process, Reliability patterns, Replicated read-model | Presenter A | 12–14 | 4 min |
| Challenges & lessons learned | Presenter B | 15 | 1 min |
| Live demo | Both | 16 | 2–3 min |
| Summary, Q&A | Presenter B | 17–18 | 2 min |

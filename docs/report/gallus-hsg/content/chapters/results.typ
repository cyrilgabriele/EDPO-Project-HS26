= Results & Insights <results>

This chapter presents the experimental results obtained during the project, covering the Kafka reliability experiments from Exercise 1 and observations from the system implementation.

== Kafka Producer Reliability: Message Loss with acks=0

*Objective:* Demonstrate that disabling acknowledgments and retries results in permanent data loss when the Kafka leader broker crashes.

*Setup:* A two-broker Kafka cluster (KRaft mode) with a `ClickStream-Producer` configured with `acks=0` and `retries=0`, emitting one event approximately every 150 ms. A `ClickStream-Consumer` logs received events.

*Procedure:* While the producer and consumer were running, the active leader broker was hard-killed using `docker stop`.

*Result:* The producer log shows `eventID=76` was sent, but the consumer log jumps directly from `eventID=75` to `eventID=77`. Event 76 was permanently lost — the producer received no error because `acks=0` means fire-and-forget.

*Insight:* With `acks=0`, the application has no mechanism to detect or recover from message loss. Remediation requires at least `acks=all`, `retries > 0`, and ideally `enable.idempotence=true`. CryptoFlow's production configuration uses `acks=all` (ADR-0001) precisely because of this risk.

== Consumer Offset Behavior

=== Safe Replay with auto.offset.reset=earliest

*Objective:* Confirm that when no committed offsets exist, `earliest` forces a full replay of retained events.

*Setup:* A fresh consumer group (`grp1`) with `auto.offset.reset=earliest`. Events were produced before the consumer started.

*Result:* The consumer received all events from offset 0, including those produced before it joined. After being killed before auto-commit and restarted, it replayed from offset 0 again.

*Insight:* Within the retention window, `earliest` guarantees deterministic replay — ideal for at-least-once processing and disaster recovery. The operational cost is that restarts may reprocess the full backlog.

=== Data Gap with auto.offset.reset=latest

*Objective:* Show that `latest` causes new consumer groups to miss all previously retained events.

*Setup:* A fresh consumer group (`grp2`) with `auto.offset.reset=latest`. Retained events existed on the topic.

*Result:* The consumer skipped all retained events and only began receiving newly produced messages. From the application's perspective, the historical data was effectively lost.

*Insight:* `latest` is unsafe for workloads that require replay of retained data. Explicit use of `earliest`, manual `seek`, or pre-seeded offsets is necessary to prevent silent data gaps. CryptoFlow uses `earliest` across all consumer groups.

== Fault Tolerance: Leader Failover Timing

*Objective:* Measure the availability gap during a leader broker crash and verify that no acknowledged data is lost.

*Setup:* A three-broker Kafka cluster with `acks=all`. The active leader was hard-killed during active production.

*Result:* The last acknowledged event before the crash was `id=212`. Leader election to a surviving broker completed in approximately 8.9 seconds. The first event acknowledged after recovery was `id=213`. The consumer processed events 211 through 271 with no gaps.

*Insight:* With `acks=all`, leader failover is an availability event, not a durability event. No acknowledged data was lost. The approximately 9-second outage is acceptable for CryptoFlow's use case. The ECST pattern (ADR-0002) further mitigates this: the portfolio service continues serving cached prices during the outage.

== Fault Tolerance: Data Loss with acks=1

*Objective:* Demonstrate that leader-only acknowledgment (`acks=1`) can lose records when the leader crashes before replication completes.

*Setup:* The same three-broker cluster, but with `acks=1` and `retries=0`. Replication lag was artificially increased via `replica.fetch.wait.max.ms=3000` to widen the loss window.

*Result:* `eventID=71` was acknowledged by the leader but never reached the followers before the crash. After leader election, the consumer detected a gap: it received `eventID=70`, then `eventID=72`, with `eventID=71` permanently lost.

*Insight:* `acks=1` only guarantees the leader appended the record — it says nothing about replication. CryptoFlow uses `acks=all` to eliminate this class of failure entirely.

== Implementation Observations

=== Event-Carried State Transfer Performance

The `LocalPriceCache` in the portfolio service, implemented as a `ConcurrentHashMap`, handles the six-symbol price feed without contention issues. With Binance pushing updates at variable rates (sub-second during active markets), the cache stays current within milliseconds under normal conditions. The 503 warm-up behavior — returning an error until the first price event arrives — was validated as an effective guard against serving stale-from-startup values.

=== Saga Compensation Reliability

The onboarding saga's compensation flow was tested by deliberately triggering failures in the user and portfolio creation workers. The flag-driven completion pattern (ADR-0012) proved effective: the BPMN process consistently reached the correct compensation branch regardless of which creation step failed. The dual Kafka listener pattern (both services listening for both compensation topics) provides an additional safety net, though no cases were observed where the primary compensation path failed.

// TODO: Run formal load tests and document throughput numbers
// TODO: Measure end-to-end latency from Binance event to portfolio valuation
// TODO: Test concurrent onboarding saga instances to validate Zeebe scaling

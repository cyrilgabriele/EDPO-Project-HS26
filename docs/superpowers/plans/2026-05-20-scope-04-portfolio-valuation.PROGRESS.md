# Scope 04 — Portfolio Valuation — Progress Snapshot

**Snapshot date:** 2026-05-20
**Plan:** `docs/superpowers/plans/2026-05-20-scope-04-portfolio-valuation.md`
**Branch:** `feat/scope-04-portfolio-valuation` (off `main`, not pushed)
**Mode:** continued locally with `superpowers:executing-plans` (subagents not spawned in this session)
**Tests:** 22/22 passing in `portfolio-service`; package build passing; Docker smoke test passing

## Tasks status (1–9)

| # | Task | Status |
|---|---|---|
| 1 | Kafka Streams deps + `portfolio.value.updated` topic bean | ✅ done — `524032a` |
| 2 | `UserSymbolKey` codec (TDD, hardened) | ✅ done — `5f900df`, `4f45357` |
| 3 | `SignedQuantity` helper (TDD) | ✅ done — `b245610` |
| 4 | `PortfolioValueAggregator` add/subtract (TDD) | ✅ done — `8c96951` |
| 5 | Full valuation topology + `TopologyTestDriver` (5 scenarios) | ✅ done — `2f173b2`, `d9d1143`, `f3e27b0` |
| 6 | IQ reader + `StreamsPortfolioValueController` (TDD) | ✅ done — `49a1371` |
| 7 | Full suite + docker smoke test | ✅ done — `6cd511a` |
| 8 | ADR-0034 for the streams app | ✅ done — `2dd5bde` |
| 9 | Update scope spec + README | ✅ done — `2499d61` |

## Branch commits (oldest → newest)

```
3399c3c docs: add scope-04 portfolio valuation implementation plan
524032a portfolio-service: add Kafka Streams deps and portfolio.value.updated topic
5f900df portfolio-service: add UserSymbolKey codec for valuation topology
4f45357 portfolio-service: harden UserSymbolKey guards (blank userId/symbol, pipe in symbol)
b245610 portfolio-service: add SignedQuantity helper for order-approved folding
8c96951 portfolio-service: add PortfolioValueAggregator add/subtract helpers
2f173b2 portfolio-service: add scope-04 valuation topology (FK join + repartition + IQ store)
d9d1143 portfolio-service: remove unused JsonDeserializer import from valuation topology test
f3e27b0 portfolio-service: use Confluent SpecificAvroSerde for portfolio.value.updated (ADR-0032)
0707359 docs: snapshot scope-04 progress (5/9 tasks done, deviations recorded)
49a1371 portfolio-service: expose interactive-query endpoint for scope-04 valuation
6cd511a portfolio-service: use JRE runtime compatible with streams state stores
2dd5bde docs: add ADR-0034 for scope-04 portfolio valuation streams app
2499d61 docs: mark scope-04 implemented and resolve open decisions
```

## Verification evidence

- `mvn -pl portfolio-service test -q` — passed; Surefire reports 22 tests, 0 failures/errors/skips.
- `mvn -pl portfolio-service -am -DskipTests package -q` — passed.
- `docker compose up -d --build portfolio-service kafka schema-registry postgres` — passed after runtime-image fix.
- `docker compose exec kafka kafka-topics --bootstrap-server kafka:29092 --describe --topic portfolio.value.updated` — topic exists with 3 partitions and `cleanup.policy=compact`.
- Smoke seed: wrote one `crypto.price.raw` tick and one `transaction.order.approved` event for `scope04-smoke-user`.
- `GET /portfolios/scope04-smoke-user/streams-value` — returned `200` with `totalUsdt > 0` and `BTCUSDT` breakdown.
- `GET /portfolios/scope04-smoke-user/value` — returned `200`, confirming the existing Postgres-backed endpoint still works.
- `kafka-console-consumer --topic portfolio.value.updated --from-beginning` — observed records keyed by `scope04-smoke-user` with Avro binary payload.

## Necessary plan deviations

1. **Task 5: `Holding(String symbol, BigDecimal quantity)` record added** as the holdings KTable value (not the spec's plain `BigDecimal qty`). Kafka Streams 3.x `KTable.join` FK extractor is value-only `Function<V, KO>`, not the spec's `BiFunction<K, V, KO>`. The Holding record carries the symbol so `Holding::symbol` works as the FK extractor. The record is declared `public` on the config class (`PortfolioValuationStreamConfig.Holding`) because Spring's `JsonSerde` reflection needs public visibility.

2. **Task 5: group-by step uses `KTable.groupBy(...)` directly**, not the spec's `positionValues.toStream().mapValues(...).groupBy(...)`. The spec's form passes a `KeyValue.pair(...)` to `KStream.groupBy`, which is the wrong API — `KStream.groupBy` only changes the key, not the value. `KTable.groupBy(KeyValueMapper<K, V, KeyValue<KR, VR>>, Grouped)` is the right API.

3. **Task 5: `PositionValue` uses the local registryless `ch.unisg.cryptoflow.events.avro.SpecificAvroSerde`** (binary, no Confluent magic byte / schema ID prefix) instead of `JsonSerde<PositionValue>`. Reason: Jackson cannot serialise Avro `decimal` logical-type fields — `Conversions$DecimalConversion.getRecommendedSchema()` throws. This is fine because `PositionValue` is purely internal state-store value; it is never written to a topic external to the streams app.

4. **Task 5: `PortfolioValue` output uses the Confluent registry-aware `io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde`** (the no-arg constructor, configured via `serdeConfig`). This is the same serde `OhlcStreamConfig` uses and complies with ADR-0032 (new Avro topics go through Schema Registry). The first attempt of Task 5 used the local serde here too — that was wrong and was fixed in `f3e27b0`.

5. **Task 6: controller test uses a local fake reader instead of Mockito.** Mockito inline self-attachment fails on the local JDK 25 runtime, so the test avoids Mockito entirely and still verifies status codes, JSON body, and path-variable propagation.

6. **Task 7: portfolio-service runtime image switched from Alpine JRE to Debian Temurin JRE with `libstdc++6`.** The smoke test first exposed missing `libstdc++.so.6` for RocksDB JNI. Installing it on Alpine then exposed a Netty tcnative/aarch64 segfault during Zeebe startup. The stable repo pattern is `eclipse-temurin:21-jre` plus `wget libstdc++6`, already used by `transaction-service`.

7. **Task 7: smoke event was seeded directly into Kafka.** The repository has a quote/dashboard REST surface for `transaction-service`, but no place-order REST endpoint; order placement is Camunda-driven. To verify the scope-04 topology deterministically, the smoke test wrote one JSON `transaction.order.approved` event and one matching `crypto.price.raw` event directly to Kafka.

## Remaining notes

- `Instant.now()` remains in the aggregator lambdas (`PortfolioValuationStreamConfig`) — wall-clock, non-deterministic in tests. Acceptable for this non-windowed IQ-backed course topology; stricter stream-time semantics would require a transformer or injected clock.
- `PortfolioValueAggregator.empty("")` seed with empty userId is overwritten by the adder/subtractor on first contribution. This follows Kafka Streams initializer constraints because the initializer cannot see the grouping key.
- Pre-existing pom warning: `portfolio-service/pom.xml` overrides the managed `spring-boot:3.5.11` version. Pre-existing before this branch and out of scope for scope-04.

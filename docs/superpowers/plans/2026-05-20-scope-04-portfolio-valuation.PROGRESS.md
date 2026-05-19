# Scope 04 — Portfolio Valuation — Progress Snapshot

**Snapshot date:** 2026-05-20
**Plan:** `docs/superpowers/plans/2026-05-20-scope-04-portfolio-valuation.md`
**Branch:** `feat/scope-04-portfolio-valuation` (off `main`, not pushed)
**Mode:** subagent-driven execution (superpowers:subagent-driven-development)
**Tests:** 20/20 passing in `portfolio-service`, no smoke test yet

## Tasks status (1–9)

| # | Task | Status |
|---|---|---|
| 1 | Kafka Streams deps + `portfolio.value.updated` topic bean | ✅ done — `524032a` |
| 2 | `UserSymbolKey` codec (TDD, hardened) | ✅ done — `5f900df`, `4f45357` |
| 3 | `SignedQuantity` helper (TDD) | ✅ done — `b245610` |
| 4 | `PortfolioValueAggregator` add/subtract (TDD) | ✅ done — `8c96951` |
| 5 | Full valuation topology + `TopologyTestDriver` (5 scenarios) | ✅ done — `2f173b2`, `d9d1143`, `f3e27b0` |
| 6 | IQ reader + `StreamsPortfolioValueController` (TDD) | ⏳ **in progress — not started** |
| 7 | Full suite + docker smoke test | ⏳ pending |
| 8 | ADR-0034 for the streams app | ⏳ pending |
| 9 | Update scope spec + README | ⏳ pending |

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
```

Working tree: clean.

## Necessary plan deviations during Task 5

The plan's Task 5 was implemented with four deviations from the spec — all forced by real API/library constraints, all justified in commit messages and code comments. **Tasks 6, 8 and 9 must respect these.**

1. **`Holding(String symbol, BigDecimal quantity)` record added** as the holdings KTable value (not the spec's plain `BigDecimal qty`). Kafka Streams 3.x `KTable.join` FK extractor is value-only `Function<V, KO>`, not the spec's `BiFunction<K, V, KO>`. The Holding record carries the symbol so `Holding::symbol` works as the FK extractor. The record is declared `public` on the config class (`PortfolioValuationStreamConfig.Holding`) because Spring's `JsonSerde` reflection needs public visibility.

2. **Group-by step uses `KTable.groupBy(...)` directly**, not the spec's `positionValues.toStream().mapValues(...).groupBy(...)`. The spec's form passes a `KeyValue.pair(...)` to `KStream.groupBy`, which is the wrong API — `KStream.groupBy` only changes the key, not the value. `KTable.groupBy(KeyValueMapper<K, V, KeyValue<KR, VR>>, Grouped)` IS the right one.

3. **`PositionValue` uses the local registryless `ch.unisg.cryptoflow.events.avro.SpecificAvroSerde`** (binary, no Confluent magic byte / schema ID prefix) instead of `JsonSerde<PositionValue>`. Reason: Jackson cannot serialise Avro `decimal` logical-type fields — `Conversions$DecimalConversion.getRecommendedSchema()` throws. This is fine because `PositionValue` is purely internal state-store value; it is never written to a topic external to the streams app.

4. **`PortfolioValue` output uses the Confluent registry-aware `io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde`** (the no-arg constructor, configured via `serdeConfig`). This is the same serde `OhlcStreamConfig` uses and complies with ADR-0032 (new Avro topics go through Schema Registry). The first attempt of Task 5 used the local serde here too — that was wrong and was fixed in `f3e27b0`.

## Open follow-up notes for later tasks

- **`{@code PortfolioValueStoreReader}` dangling reference** in `PortfolioValuationStreamConfig` class-level Javadoc (around line 55) was temporarily downgraded from `{@link}` to `{@code}` because Task 6 hadn't created the class yet. Task 6 should upgrade it back to `{@link PortfolioValueStoreReader}` once the file exists.

- **`Instant.now()` in the aggregator lambdas** (lines 202–203 of `PortfolioValuationStreamConfig.java`) — wall-clock, non-deterministic in tests (the tests do not assert on `asOf`). Acceptable for a non-windowed IQ-backed topology in a course project. If the report wants stricter semantics, swap for `ProcessorContext.currentStreamTimeMs()` via a Transformer or inject a `Supplier<Instant>`.

- **`PortfolioValueAggregator.empty("")` seed with empty userId** is overwritten by the adder/subtractor on first contribution. Standard Kafka Streams idiom (initializer can't see the grouping key) but worth a one-line comment near the call site.

- **Pre-existing pom warning:** `portfolio-service/pom.xml` line ~114 overrides the managed `spring-boot:3.5.11` version. Pre-existing before this branch — out of scope for scope-04.

## Where to continue from here

Pick up at **Task 6** in the plan: "Interactive query reader + REST endpoint (TDD)". The plan text for Task 6 is in `docs/superpowers/plans/2026-05-20-scope-04-portfolio-valuation.md` and is **mostly correct** — but Task 6 references `PortfolioValuationStreamConfig.PORTFOLIO_VALUE_STORE` from Task 5, which exists (value: `"portfolio-value-store"`). No deviation needed for Task 6 as written.

After Task 6, continue with Tasks 7 (smoke test), 8 (ADR-0034) and 9 (docs updates). All three are written as in the plan and should not require deviations.

### Resuming with subagent-driven mode

```
Skill: superpowers:subagent-driven-development
TaskList currently: 1–5 completed, 6 in_progress, 7–9 pending
Next agent dispatch: Task 6 — use the full prompt template from
  /Users/ioannis/.claude/plugins/cache/claude-plugins-official/superpowers/5.1.0/skills/subagent-driven-development/implementer-prompt.md
  pasting Task 6 text verbatim from the plan.
```

Recommended model per task:
- Task 6 (controller + reader, ~80 lines + MockMvc tests): `sonnet`
- Task 7 (mvn + docker compose + curl smoke): `sonnet`, but expect manual back-and-forth — docker compose env vars need a real `.env` file
- Task 8 (ADR — pure docs): `sonnet`
- Task 9 (3 doc files): `sonnet`

Avoid `haiku` for Task 6 — the MockMvc + JSON path-assertions test has been a source of confusion for smaller models in this codebase.

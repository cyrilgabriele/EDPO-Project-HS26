# Scope 04 — Portfolio Valuation Streams App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kafka Streams application inside `portfolio-service` that continuously computes each user's total USDT portfolio value (holdings × current price) and exposes it through interactive queries, demonstrating Table-Table Join, Multiphase Repartitioning, Processing with Local State and Interactive Queries patterns.

**Architecture:** New `@Configuration` (`PortfolioValuationStreamConfig`) inside `portfolio-service` hosts a Kafka Streams topology with its own `applicationId` (`portfolio-service-valuation`). Inputs are `transaction.order.approved` (JSON, existing) and `crypto.price.raw` (JSON, existing). The topology folds order events into a **holdings KTable** keyed by `userId|symbol`, materialises `crypto.price.raw` into a **prices KTable** keyed by `symbol`, **foreign-key joins** them on symbol, then **`groupBy(userId)` + aggregate** (the multiphase repartition step) into a **portfolio-value KTable** keyed by `userId`. The aggregate is materialised locally for **interactive queries** and forwarded to a new compacted output topic `portfolio.value.updated`. The existing JPA-backed `/portfolios/{userId}/value` endpoint stays untouched; a new `GET /portfolios/{userId}/streams-value` endpoint reads the Streams state store, enabling the spec's state-store-vs-Postgres comparison story.

**Tech Stack:** Kafka Streams 3.x DSL, Confluent `SpecificAvroSerde` for `PortfolioValue` output, Spring Kafka `JsonSerde` for JSON inputs, JUnit 5 + `TopologyTestDriver`, Spring Boot 3.5, Java 21.

---

## Locked decisions (recap)

- **Host:** inside `portfolio-service` (alongside existing JPA/Kafka stack).
- **Holdings source:** replay `transaction.order.approved`, fold signed qty into KTable.
- **Price input:** `crypto.price.raw` (USDT-denominated, ADR-0028/0031 doctrine — aggregate in USDT, convert at read).
- **Processing guarantee:** `at_least_once` with idempotent signed-sum folds (commutative+associative — retries are safe).
- **Composite key encoding:** `String` (`userId|symbol`), matching the convention used in `TransactionMatchingTopology.PendingBid.serialize`.
- **Signed quantity:** currently `+amount` for every event (transaction-service only emits buys today). A `signedQuantity(OrderApprovedEvent)` helper is introduced now so adding a sell-side later is a one-line change.
- **IQ:** new `GET /portfolios/{userId}/streams-value` endpoint; single-instance assumption in dev (no cross-instance metadata redirect). Existing `/portfolios/{userId}/value` (Postgres-backed) is left untouched for the comparison.

## File map

**Create:**
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKey.java` — pure helper: `(userId, symbol)` ↔ `String` codec.
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregator.java` — pure adder/subtractor that maintains `PortfolioValue` (breakdown by symbol + total).
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantity.java` — pure helper turning an `OrderApprovedEvent` into a signed BigDecimal.
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationStreamConfig.java` — Spring `@Configuration` + `@EnableKafkaStreams` + topology builder.
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueStoreReader.java` — IQ reader against the `portfolio-value-store`.
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueTopicConfig.java` — `NewTopic` bean for `portfolio.value.updated` (compacted).
- `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/adapter/in/web/StreamsPortfolioValueController.java` — REST endpoint `GET /portfolios/{userId}/streams-value`.
- `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKeyTest.java`
- `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregatorTest.java`
- `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantityTest.java`
- `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationTopologyTests.java` — `TopologyTestDriver` end-to-end.
- `docs/adrs/0034_portfolio_valuation_streams_app.md`

**Modify:**
- `portfolio-service/pom.xml` — add `kafka-streams` + `kafka-streams-avro-serde`.
- `portfolio-service/src/main/resources/application.yml` — add `crypto.kafka.topic.portfolio-value`, `portfolio.application-id`, set Streams default serdes.
- `docs/event-processes/04-portfolio-valuation.md` — flip status to `implemented`; resolve open decisions list with links to ADR-0034.
- `docs/adrs/0000_template_architecture_decisions.md` (no edit, just reference for new ADR).
- `README.md` — append `portfolio.value.updated` topic to the Kafka section so the value materialises in the topic table.

**No change needed:**
- `PortfolioValue.avsc` (already exists in `shared-events/src/main/avro/PortfolioValue.avsc`).
- `OrderApprovedEvent.java` (already carries `userId`, `symbol`, `amount`).
- `CryptoPriceUpdatedEvent.java` (already carries `symbol`, `price`, `timestamp`).
- `docker/docker-compose.yml` (no new env vars needed; Streams uses the bootstrap and schema-registry URLs already configured for portfolio-service).

## Topology shape (reference, not code)

```
transaction.order.approved (KStream)                  crypto.price.raw (KStream)
        │                                                       │
        ▼ selectKey(userId|symbol) + groupByKey                 ▼ selectKey(symbol) + toTable
        │                                                       │
        ▼ aggregate(signed sum) → holdings KTable        prices KTable (latest BigDecimal per symbol)
        │  key: "userId|symbol"  value: BigDecimal qty   │  key: "BTCUSDT"  value: BigDecimal price
        │                                                │
        └──────── FK join, key extractor (us, _) ────────┘
                  → symbol field of UserSymbolKey
                           │
                           ▼ valueJoiner: PositionValue(symbol, qty, qty*price)
                  position-value KTable
                  key: "userId|symbol"  value: PositionValue
                           │
                           ▼ groupBy(userId, positionValue)   ← repartition (multiphase!)
                           │   aggregate(initializer, adder, subtractor)
                           ▼
                  portfolio-value KTable (Materialized "portfolio-value-store")
                  key: userId  value: PortfolioValue (Avro)
                           │
                ┌──────────┴─────────────┐
                ▼                        ▼
   portfolio.value.updated      Interactive query
   (compacted, key = userId)    GET /portfolios/{userId}/streams-value
```

---

## Task 1 — Add Kafka Streams dependencies and topic + app-id config

**Files:**
- Modify: `portfolio-service/pom.xml`
- Modify: `portfolio-service/src/main/resources/application.yml`
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueTopicConfig.java`

- [ ] **Step 1: Add Kafka Streams + Avro serde to pom**

Insert into `portfolio-service/pom.xml` after the existing `spring-kafka` dependency (around the comment `<!-- Confluent Avro serdes for reference.fx.rate ... -->`):

```xml
        <!-- Kafka Streams for the scope-04 portfolio valuation topology -->
        <dependency>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-streams</artifactId>
        </dependency>
        <dependency>
            <groupId>io.confluent</groupId>
            <artifactId>kafka-streams-avro-serde</artifactId>
        </dependency>
```

- [ ] **Step 2: Run a compile to confirm dependencies resolve**

Run: `mvn -pl portfolio-service -am compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Add topic name + application-id + Streams defaults to `application.yml`**

In `portfolio-service/src/main/resources/application.yml`, append `portfolio-value: portfolio.value.updated` to the `crypto.kafka.topic` block:

```yaml
crypto:
  kafka:
    topic:
      price-raw: crypto.price.raw
      price-raw-dlt: crypto.price.raw.DLT
      portfolio-compensation: crypto.portfolio.compensation
      user-compensation: crypto.user.compensation
      order-approved: transaction.order.approved
      fx-rate: reference.fx.rate
      user-display-currency: user.display-currency
      portfolio-value: portfolio.value.updated
```

Add a new top-level block after `crypto:`:

```yaml
# Scope-04 portfolio valuation Kafka Streams app (event-processes/04-portfolio-valuation.md).
# Inputs: transaction.order.approved (JSON), crypto.price.raw (JSON).
# Output: portfolio.value.updated (Avro, compacted, keyed by userId).
portfolio:
  valuation:
    application-id: ${PORTFOLIO_VALUATION_APP_ID:portfolio-service-valuation}
```

- [ ] **Step 4: Create the output topic bean**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueTopicConfig.java`:

```java
package ch.unisg.cryptoflow.portfolio.streams;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

/**
 * Compacted output topic for the scope-04 portfolio valuation streams app.
 * Keyed by userId so the latest PortfolioValue per user is always retained.
 */
@Configuration
public class PortfolioValueTopicConfig {

    @Bean
    public NewTopic portfolioValueTopic(@Value("${crypto.kafka.topic.portfolio-value}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT))
                .build();
    }
}
```

- [ ] **Step 5: Compile**

Run: `mvn -pl portfolio-service -am compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add portfolio-service/pom.xml \
        portfolio-service/src/main/resources/application.yml \
        portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueTopicConfig.java
git commit -m "portfolio-service: add Kafka Streams deps and portfolio.value.updated topic"
```

---

## Task 2 — `UserSymbolKey` String codec (TDD)

A composite key the topology uses for the holdings and position-value KTables. Plain `String` so we don't need a custom serde.

**Files:**
- Create: `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKeyTest.java`
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKey.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.unisg.cryptoflow.portfolio.streams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSymbolKeyTest {

    @Test
    void encodesUserAndSymbolWithPipeDelimiter() {
        assertThat(UserSymbolKey.encode("u-1", "BTCUSDT")).isEqualTo("u-1|BTCUSDT");
    }

    @Test
    void roundTripsThroughDecode() {
        String encoded = UserSymbolKey.encode("u-42", "ETHUSDT");
        UserSymbolKey decoded = UserSymbolKey.decode(encoded);
        assertThat(decoded.userId()).isEqualTo("u-42");
        assertThat(decoded.symbol()).isEqualTo("ETHUSDT");
    }

    @Test
    void uppercasesSymbolOnEncode() {
        assertThat(UserSymbolKey.encode("u", "btcusdt")).isEqualTo("u|BTCUSDT");
    }

    @Test
    void rejectsUserIdContainingDelimiter() {
        assertThatThrownBy(() -> UserSymbolKey.encode("u|1", "BTCUSDT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsMissingDelimiter() {
        assertThatThrownBy(() -> UserSymbolKey.decode("nodelimiter"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

Run: `mvn -pl portfolio-service test -Dtest=UserSymbolKeyTest -q`
Expected: compilation error (`UserSymbolKey` does not exist).

- [ ] **Step 3: Implement `UserSymbolKey`**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKey.java`:

```java
package ch.unisg.cryptoflow.portfolio.streams;

import java.util.Locale;

/**
 * Composite key for the holdings and position-value KTables in the scope-04
 * valuation topology. Encoded as {@code "userId|SYMBOL"} so a plain String
 * Serde is enough (no custom Avro/JSON serde to maintain).
 */
public record UserSymbolKey(String userId, String symbol) {

    private static final String DELIMITER = "|";

    public static String encode(String userId, String symbol) {
        if (userId == null || symbol == null) {
            throw new IllegalArgumentException("userId and symbol must be non-null");
        }
        if (userId.contains(DELIMITER)) {
            throw new IllegalArgumentException("userId must not contain '" + DELIMITER + "'");
        }
        return userId + DELIMITER + symbol.toUpperCase(Locale.ROOT);
    }

    public static UserSymbolKey decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded key must be non-null");
        }
        int idx = encoded.indexOf(DELIMITER);
        if (idx < 0) {
            throw new IllegalArgumentException("missing '" + DELIMITER + "' in key: " + encoded);
        }
        return new UserSymbolKey(encoded.substring(0, idx), encoded.substring(idx + 1));
    }
}
```

- [ ] **Step 4: Re-run test, confirm it passes**

Run: `mvn -pl portfolio-service test -Dtest=UserSymbolKeyTest -q`
Expected: BUILD SUCCESS, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKey.java \
        portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/UserSymbolKeyTest.java
git commit -m "portfolio-service: add UserSymbolKey codec for valuation topology"
```

---

## Task 3 — `SignedQuantity` helper (TDD)

Today every `OrderApprovedEvent` is a buy (positive). Centralising this means the day a sell flow ships, only this helper changes.

**Files:**
- Create: `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantityTest.java`
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantity.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignedQuantityTest {

    @Test
    void returnsPositiveAmountForBuyApproval() {
        OrderApprovedEvent buy = new OrderApprovedEvent(
                "tx-1", "u-1", "BTCUSDT",
                new BigDecimal("0.5"), new BigDecimal("60000.00"),
                Instant.parse("2026-05-20T10:00:00Z"));

        assertThat(SignedQuantity.of(buy)).isEqualByComparingTo("0.5");
    }

    @Test
    void zeroWhenAmountIsNull() {
        OrderApprovedEvent malformed = new OrderApprovedEvent(
                "tx-2", "u-1", "BTCUSDT",
                null, new BigDecimal("60000.00"),
                Instant.parse("2026-05-20T10:00:00Z"));

        assertThat(SignedQuantity.of(malformed)).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

Run: `mvn -pl portfolio-service test -Dtest=SignedQuantityTest -q`
Expected: compilation error.

- [ ] **Step 3: Implement `SignedQuantity`**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantity.java`:

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.OrderApprovedEvent;

import java.math.BigDecimal;

/**
 * Maps an {@link OrderApprovedEvent} to the signed quantity to fold into the
 * holdings KTable. transaction-service only emits buy approvals today, so
 * every event is treated as positive. When a sell flow is added, route on the
 * side field here.
 */
public final class SignedQuantity {

    private SignedQuantity() {}

    public static BigDecimal of(OrderApprovedEvent event) {
        if (event == null || event.amount() == null) {
            return BigDecimal.ZERO;
        }
        return event.amount();
    }
}
```

- [ ] **Step 4: Re-run test, confirm it passes**

Run: `mvn -pl portfolio-service test -Dtest=SignedQuantityTest -q`
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantity.java \
        portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/SignedQuantityTest.java
git commit -m "portfolio-service: add SignedQuantity helper for order-approved folding"
```

---

## Task 4 — `PortfolioValueAggregator` add/subtract helpers (TDD)

Pure functions used by the final `KTable.groupBy(...).aggregate(initializer, adder, subtractor)` step. Maintains a `PortfolioValue` Avro record with a breakdown list keyed by symbol and a total in USDT.

**Files:**
- Create: `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregatorTest.java`
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregator.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioValueAggregatorTest {

    private static final Instant T1 = Instant.parse("2026-05-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-05-20T10:00:01Z");

    @Test
    void emptyInitializerHasZeroTotalAndNoBreakdown() {
        PortfolioValue empty = PortfolioValueAggregator.empty("u-1");
        assertThat(empty.getUserId()).isEqualTo("u-1");
        assertThat(empty.getTotalUsdt()).isEqualByComparingTo("0");
        assertThat(empty.getBreakdown()).isEmpty();
    }

    @Test
    void addInsertsNewPositionAndRecomputesTotal() {
        PortfolioValue start = PortfolioValueAggregator.empty("u-1");
        PortfolioValue after = PortfolioValueAggregator.add(
                "u-1", position("BTCUSDT", "0.5", "30000"), start, T1);

        assertThat(after.getTotalUsdt()).isEqualByComparingTo("30000");
        assertThat(after.getBreakdown()).hasSize(1);
        assertThat(after.getBreakdown().get(0).getSymbol()).isEqualTo("BTCUSDT");
        assertThat(after.getBreakdown().get(0).getValueUsdt()).isEqualByComparingTo("30000");
        assertThat(after.getAsOf()).isEqualTo(T1);
    }

    @Test
    void addReplacesExistingPositionForSameSymbol() {
        PortfolioValue start = PortfolioValueAggregator.add(
                "u-1", position("BTCUSDT", "0.5", "30000"),
                PortfolioValueAggregator.empty("u-1"), T1);

        PortfolioValue after = PortfolioValueAggregator.add(
                "u-1", position("BTCUSDT", "0.5", "31000"), start, T2);

        assertThat(after.getBreakdown()).hasSize(1);
        assertThat(after.getBreakdown().get(0).getValueUsdt()).isEqualByComparingTo("31000");
        assertThat(after.getTotalUsdt()).isEqualByComparingTo("31000");
    }

    @Test
    void subtractRemovesPositionAndRecomputesTotal() {
        PortfolioValue twoPositions = PortfolioValueAggregator.add(
                "u-1", position("ETHUSDT", "10", "2000"),
                PortfolioValueAggregator.add(
                        "u-1", position("BTCUSDT", "0.5", "30000"),
                        PortfolioValueAggregator.empty("u-1"), T1),
                T1);
        assertThat(twoPositions.getTotalUsdt()).isEqualByComparingTo("32000");

        PortfolioValue after = PortfolioValueAggregator.subtract(
                "u-1", position("BTCUSDT", "0.5", "30000"), twoPositions, T2);

        assertThat(after.getBreakdown()).hasSize(1);
        assertThat(after.getBreakdown().get(0).getSymbol()).isEqualTo("ETHUSDT");
        assertThat(after.getTotalUsdt()).isEqualByComparingTo("2000");
    }

    @Test
    void breakdownIsSortedBySymbol() {
        PortfolioValue v = PortfolioValueAggregator.add(
                "u-1", position("ETHUSDT", "1", "2000"),
                PortfolioValueAggregator.add(
                        "u-1", position("BTCUSDT", "0.1", "6000"),
                        PortfolioValueAggregator.empty("u-1"), T1),
                T2);

        assertThat(v.getBreakdown()).extracting(PositionValue::getSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT");
    }

    private static PositionValue position(String symbol, String qty, String value) {
        return PositionValue.newBuilder()
                .setSymbol(symbol)
                .setQuantity(new BigDecimal(qty))
                .setValueUsdt(new BigDecimal(value))
                .build();
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

Run: `mvn -pl portfolio-service test -Dtest=PortfolioValueAggregatorTest -q`
Expected: compilation error.

- [ ] **Step 3: Implement `PortfolioValueAggregator`**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregator.java`:

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure adder/subtractor used by the final {@code KTable.groupBy(...).aggregate(...)}
 * step of the scope-04 valuation topology. Maintains a {@link PortfolioValue}
 * with a per-symbol breakdown and a running USDT total.
 *
 * <p>The Kafka Streams runtime calls {@code subtractor} with the prior
 * upstream value and {@code adder} with the new one, so a symbol update
 * round-trips cleanly: subtractor removes the old contribution, adder inserts
 * the new one.
 */
public final class PortfolioValueAggregator {

    private PortfolioValueAggregator() {}

    public static PortfolioValue empty(String userId) {
        return PortfolioValue.newBuilder()
                .setUserId(userId)
                .setTotalUsdt(BigDecimal.ZERO)
                .setBreakdown(new ArrayList<>())
                .setAsOf(Instant.EPOCH)
                .build();
    }

    public static PortfolioValue add(String userId, PositionValue pv, PortfolioValue current, Instant asOf) {
        List<PositionValue> next = new ArrayList<>(current.getBreakdown());
        next.removeIf(existing -> existing.getSymbol().equals(pv.getSymbol()));
        next.add(pv);
        next.sort(Comparator.comparing(PositionValue::getSymbol));
        return PortfolioValue.newBuilder()
                .setUserId(userId)
                .setBreakdown(next)
                .setTotalUsdt(sumValues(next))
                .setAsOf(asOf)
                .build();
    }

    public static PortfolioValue subtract(String userId, PositionValue pv, PortfolioValue current, Instant asOf) {
        List<PositionValue> next = new ArrayList<>(current.getBreakdown());
        next.removeIf(existing -> existing.getSymbol().equals(pv.getSymbol()));
        return PortfolioValue.newBuilder()
                .setUserId(userId)
                .setBreakdown(next)
                .setTotalUsdt(sumValues(next))
                .setAsOf(asOf)
                .build();
    }

    private static BigDecimal sumValues(List<PositionValue> positions) {
        BigDecimal total = BigDecimal.ZERO;
        for (PositionValue p : positions) {
            total = total.add(p.getValueUsdt());
        }
        return total;
    }
}
```

- [ ] **Step 4: Re-run test, confirm it passes**

Run: `mvn -pl portfolio-service test -Dtest=PortfolioValueAggregatorTest -q`
Expected: BUILD SUCCESS, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregator.java \
        portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueAggregatorTest.java
git commit -m "portfolio-service: add PortfolioValueAggregator add/subtract helpers"
```

---

## Task 5 — Build full valuation topology + end-to-end `TopologyTestDriver` test (TDD)

This is the meaty task. We write a single end-to-end `TopologyTestDriver` test that drives both input topics and observes the `portfolio.value.updated` output. Then we implement `PortfolioValuationStreamConfig`. The class exposes a static `buildTopology(StreamsBuilder, Properties)` so the test can drive it without Spring.

**Files:**
- Create: `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationTopologyTests.java`
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationStreamConfig.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import ch.unisg.cryptoflow.events.avro.SpecificAvroSerde;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerdeConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioValuationTopologyTests {

    private static final String ORDER_TOPIC = "transaction.order.approved";
    private static final String PRICE_TOPIC = "crypto.price.raw";
    private static final String OUTPUT_TOPIC = "portfolio.value.updated";
    private static final String SCHEMA_REGISTRY_SCOPE = "scope-04-test";
    private static final String SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    private TopologyTestDriver driver;
    private TestInputTopic<String, OrderApprovedEvent> orderInput;
    private TestInputTopic<String, CryptoPriceUpdatedEvent> priceInput;
    private TestOutputTopic<String, PortfolioValue> portfolioOutput;
    private SpecificAvroSerde<PortfolioValue> portfolioValueSerde;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "scope-04-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);

        StreamsBuilder builder = new StreamsBuilder();
        PortfolioValuationStreamConfig.buildTopology(
                builder,
                ORDER_TOPIC,
                PRICE_TOPIC,
                OUTPUT_TOPIC,
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL));
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        orderInput = driver.createInputTopic(ORDER_TOPIC, new StringSerializer(),
                new JsonSerializer<>());
        priceInput = driver.createInputTopic(PRICE_TOPIC, new StringSerializer(),
                new JsonSerializer<>());

        portfolioValueSerde = new SpecificAvroSerde<>(PortfolioValue.class, PortfolioValue.getClassSchema());
        portfolioValueSerde.configure(
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL),
                false);
        portfolioOutput = driver.createOutputTopic(OUTPUT_TOPIC, new StringDeserializer(),
                portfolioValueSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.close();
        if (portfolioValueSerde != null) portfolioValueSerde.close();
        MockSchemaRegistry.dropScope(SCHEMA_REGISTRY_SCOPE);
    }

    @Test
    void emitsPortfolioValueWhenOrderArrivesAfterPrice() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getUserId()).isEqualTo("u-1");
        assertThat(latest.getBreakdown()).hasSize(1);
        assertThat(latest.getBreakdown().get(0).getSymbol()).isEqualTo("BTCUSDT");
        assertThat(latest.getBreakdown().get(0).getQuantity()).isEqualByComparingTo("0.5");
        assertThat(latest.getBreakdown().get(0).getValueUsdt()).isEqualByComparingTo("15000");
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("15000");
    }

    @Test
    void recomputesWhenPriceUpdates() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));
        portfolioOutput.readKeyValuesToList();

        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "31000"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("15500");
    }

    @Test
    void aggregatesAcrossMultipleSymbolsPerUser() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        priceInput.pipeInput("ETHUSDT", price("ETHUSDT", "2000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));
        orderInput.pipeInput("u-1", buy("tx-2", "u-1", "ETHUSDT", "10", "1990"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("35000");
        assertThat(latest.getBreakdown()).extracting(PositionValue::getSymbol)
                .containsExactly("BTCUSDT", "ETHUSDT");
    }

    @Test
    void portfolioValueStoreIsQueryable() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));

        KeyValueStore<String, PortfolioValue> store = driver.getKeyValueStore(
                PortfolioValuationStreamConfig.PORTFOLIO_VALUE_STORE);
        PortfolioValue stored = store.get("u-1");
        assertThat(stored).isNotNull();
        assertThat(stored.getTotalUsdt()).isEqualByComparingTo("15000");
    }

    @Test
    void secondBuyAddsToExistingHoldings() {
        priceInput.pipeInput("BTCUSDT", price("BTCUSDT", "30000"));
        orderInput.pipeInput("u-1", buy("tx-1", "u-1", "BTCUSDT", "0.5", "29500"));
        orderInput.pipeInput("u-1", buy("tx-2", "u-1", "BTCUSDT", "0.25", "30500"));

        PortfolioValue latest = lastValue(portfolioOutput.readKeyValuesToList());
        assertThat(latest.getBreakdown()).hasSize(1);
        assertThat(latest.getBreakdown().get(0).getQuantity()).isEqualByComparingTo("0.75");
        assertThat(latest.getTotalUsdt()).isEqualByComparingTo("22500");
    }

    private static OrderApprovedEvent buy(String txId, String userId, String symbol,
                                          String amount, String price) {
        return new OrderApprovedEvent(txId, userId, symbol,
                new BigDecimal(amount), new BigDecimal(price),
                Instant.parse("2026-05-20T10:00:00Z"));
    }

    private static CryptoPriceUpdatedEvent price(String symbol, String price) {
        return new CryptoPriceUpdatedEvent("evt-" + symbol, symbol,
                new BigDecimal(price), Instant.parse("2026-05-20T10:00:00Z"));
    }

    private static <V> V lastValue(List<? extends org.apache.kafka.streams.KeyValue<String, V>> records) {
        assertThat(records).isNotEmpty();
        return records.get(records.size() - 1).value;
    }
}
```

- [ ] **Step 2: Run test, confirm it fails (compile error)**

Run: `mvn -pl portfolio-service test -Dtest=PortfolioValuationTopologyTests -q`
Expected: `PortfolioValuationStreamConfig` does not exist.

- [ ] **Step 3: Implement `PortfolioValuationStreamConfig`**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationStreamConfig.java`:

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import ch.unisg.cryptoflow.events.OrderApprovedEvent;
import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Scope-04 portfolio valuation topology (event-processes/04-portfolio-valuation.md,
 * ADR-0034).
 *
 * <p>Lives inside portfolio-service so the interactive-query endpoint can read
 * the materialised state store directly. Runs as a separate consumer group
 * ({@code portfolio-service-valuation}) from the existing
 * {@code @KafkaListener} consumers.
 *
 * <p>Patterns demonstrated:
 * <ul>
 *   <li>State-and-Stream-Table duality — both inputs become KTables.</li>
 *   <li>FK Table-Table Join — holdings (key {@code userId|symbol}) × prices
 *       (key {@code symbol}) on the symbol component.</li>
 *   <li>Multiphase Repartitioning — {@code KTable.groupBy(userId)} triggers
 *       an internal repartition before the per-user aggregate.</li>
 *   <li>Processing with Local State — materialised
 *       {@code portfolio-value-store} backs the IQ endpoint.</li>
 *   <li>Interactive Queries — see {@link PortfolioValueStoreReader}.</li>
 * </ul>
 */
@Configuration
@EnableKafkaStreams
public class PortfolioValuationStreamConfig {

    public static final String HOLDINGS_STORE = "portfolio-valuation-holdings-store";
    public static final String PRICES_STORE = "portfolio-valuation-prices-store";
    public static final String POSITION_VALUE_STORE = "portfolio-valuation-position-value-store";
    public static final String PORTFOLIO_VALUE_STORE = "portfolio-value-store";

    @Value("${portfolio.valuation.application-id}")
    private String applicationId;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${crypto.kafka.topic.order-approved}")
    private String orderApprovedTopic;

    @Value("${crypto.kafka.topic.price-raw}")
    private String priceRawTopic;

    @Value("${crypto.kafka.topic.portfolio-value}")
    private String portfolioValueTopic;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                org.apache.kafka.streams.errors.LogAndContinueExceptionHandler.class.getName());
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), "earliest");
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public KStream<String, PortfolioValue> portfolioValuationTopology(StreamsBuilder builder) {
        return buildTopology(
                builder,
                orderApprovedTopic,
                priceRawTopic,
                portfolioValueTopic,
                Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl));
    }

    static KStream<String, PortfolioValue> buildTopology(
            StreamsBuilder builder,
            String orderApprovedTopic,
            String priceRawTopic,
            String portfolioValueTopic,
            Map<String, ?> serdeConfig) {

        Serde<String> keySerde = Serdes.String();

        JsonSerde<OrderApprovedEvent> orderSerde = new JsonSerde<>(OrderApprovedEvent.class);
        orderSerde.deserializer().addTrustedPackages("ch.unisg.cryptoflow.events");
        orderSerde.deserializer().setUseTypeHeaders(false);

        JsonSerde<CryptoPriceUpdatedEvent> priceSerde = new JsonSerde<>(CryptoPriceUpdatedEvent.class);
        priceSerde.deserializer().addTrustedPackages("ch.unisg.cryptoflow.events");
        priceSerde.deserializer().setUseTypeHeaders(false);

        JsonSerde<BigDecimal> bigDecimalSerde = new JsonSerde<>(BigDecimal.class);
        bigDecimalSerde.deserializer().setUseTypeHeaders(false);

        JsonSerde<PositionValue> positionValueSerde = new JsonSerde<>(PositionValue.class);
        positionValueSerde.deserializer().setUseTypeHeaders(false);

        SpecificAvroSerde<PortfolioValue> portfolioValueSerde =
                new SpecificAvroSerde<>(PortfolioValue.class, PortfolioValue.getClassSchema());
        portfolioValueSerde.configure(serdeConfig, false);

        // ── Holdings KTable: signed-sum fold per (userId, symbol) ────────────
        KTable<String, BigDecimal> holdings = builder
                .stream(orderApprovedTopic, Consumed.with(keySerde, orderSerde))
                .filter((k, ev) -> ev != null && ev.userId() != null && ev.symbol() != null)
                .map((k, ev) -> KeyValue.pair(
                        UserSymbolKey.encode(ev.userId(), ev.symbol()),
                        SignedQuantity.of(ev)))
                .groupByKey(Grouped.with(keySerde, bigDecimalSerde))
                .aggregate(
                        () -> BigDecimal.ZERO,
                        (key, delta, current) -> current.add(delta),
                        Materialized.<String, BigDecimal, KeyValueStore<Bytes, byte[]>>as(HOLDINGS_STORE)
                                .withKeySerde(keySerde)
                                .withValueSerde(bigDecimalSerde));

        // ── Prices KTable: latest price per symbol ──────────────────────────
        KTable<String, BigDecimal> prices = builder
                .stream(priceRawTopic, Consumed.with(keySerde, priceSerde))
                .filter((k, ev) -> ev != null && ev.symbol() != null && ev.price() != null)
                .map((k, ev) -> KeyValue.pair(ev.symbol().toUpperCase(java.util.Locale.ROOT), ev.price()))
                .toTable(Materialized.<String, BigDecimal, KeyValueStore<Bytes, byte[]>>as(PRICES_STORE)
                        .withKeySerde(keySerde)
                        .withValueSerde(bigDecimalSerde));

        // ── FK Table-Table Join: holdings × prices on symbol ────────────────
        KTable<String, PositionValue> positionValues = holdings.join(
                prices,
                (userSymbolKey, qty) -> UserSymbolKey.decode(userSymbolKey).symbol(),
                (qty, price) -> PositionValue.newBuilder()
                        .setSymbol("")              // filled below from the key
                        .setQuantity(qty)
                        .setValueUsdt(qty.multiply(price))
                        .build(),
                Materialized.<String, PositionValue, KeyValueStore<Bytes, byte[]>>as(POSITION_VALUE_STORE)
                        .withKeySerde(keySerde)
                        .withValueSerde(positionValueSerde));

        // ── Multiphase repartition: groupBy(userId) + aggregate ─────────────
        KTable<String, PortfolioValue> portfolioValues = positionValues
                .toStream()
                .mapValues((compositeKey, pv) -> withSymbol(pv, UserSymbolKey.decode(compositeKey).symbol()))
                .groupBy(
                        (compositeKey, pv) -> KeyValue.pair(UserSymbolKey.decode(compositeKey).userId(), pv),
                        Grouped.with(keySerde, positionValueSerde))
                .aggregate(
                        () -> PortfolioValueAggregator.empty(""),
                        (userId, pv, agg) -> PortfolioValueAggregator.add(userId, pv, agg, Instant.now()),
                        (userId, pv, agg) -> PortfolioValueAggregator.subtract(userId, pv, agg, Instant.now()),
                        Materialized.<String, PortfolioValue, KeyValueStore<Bytes, byte[]>>as(PORTFOLIO_VALUE_STORE)
                                .withKeySerde(keySerde)
                                .withValueSerde(portfolioValueSerde));

        KStream<String, PortfolioValue> output = portfolioValues
                .toStream()
                .filter((userId, pv) -> pv != null);

        output.to(portfolioValueTopic, Produced.with(keySerde, portfolioValueSerde));
        return output;
    }

    private static PositionValue withSymbol(PositionValue pv, String symbol) {
        return PositionValue.newBuilder(pv).setSymbol(symbol).build();
    }
}
```

- [ ] **Step 4: Re-run test, confirm it passes**

Run: `mvn -pl portfolio-service test -Dtest=PortfolioValuationTopologyTests -q`
Expected: BUILD SUCCESS, 5 tests passed. The mock schema registry is provided by `kafka-schema-registry-client` (transitive via `kafka-streams-avro-serde`). If the test fails with `ClassNotFoundException io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry`, add this to `portfolio-service/pom.xml` test scope:

```xml
        <dependency>
            <groupId>io.confluent</groupId>
            <artifactId>kafka-schema-registry-client</artifactId>
            <scope>test</scope>
        </dependency>
```

Re-run. Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationStreamConfig.java \
        portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValuationTopologyTests.java \
        portfolio-service/pom.xml
git commit -m "portfolio-service: add scope-04 valuation topology (FK join + repartition + IQ store)"
```

---

## Task 6 — Interactive query reader + REST endpoint (TDD)

Reads the local `portfolio-value-store` and serves the latest `PortfolioValue` for a user. Mirrors the `KafkaStreamsScoutDashboardStatsReader` shape so the codebase stays consistent.

**Files:**
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueStoreReader.java`
- Create: `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/adapter/in/web/StreamsPortfolioValueController.java`
- Create: `portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/adapter/in/web/StreamsPortfolioValueControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

```java
package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import ch.unisg.cryptoflow.portfolio.streams.PortfolioValueStoreReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StreamsPortfolioValueControllerTest {

    private PortfolioValueStoreReader reader;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reader = Mockito.mock(PortfolioValueStoreReader.class);
        StreamsPortfolioValueController controller = new StreamsPortfolioValueController(
                reader, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returns404WhenStoreHasNoEntryForUser() throws Exception {
        Mockito.when(reader.findByUserId(eq("u-1"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/portfolios/u-1/streams-value"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBreakdownAndTotalFromStore() throws Exception {
        PortfolioValue value = PortfolioValue.newBuilder()
                .setUserId("u-1")
                .setTotalUsdt(new BigDecimal("32000"))
                .setBreakdown(List.of(
                        PositionValue.newBuilder()
                                .setSymbol("BTCUSDT")
                                .setQuantity(new BigDecimal("0.5"))
                                .setValueUsdt(new BigDecimal("30000"))
                                .build(),
                        PositionValue.newBuilder()
                                .setSymbol("ETHUSDT")
                                .setQuantity(new BigDecimal("1"))
                                .setValueUsdt(new BigDecimal("2000"))
                                .build()))
                .setAsOf(Instant.parse("2026-05-20T10:00:00Z"))
                .build();
        Mockito.when(reader.findByUserId(eq("u-1"))).thenReturn(Optional.of(value));

        mockMvc.perform(get("/portfolios/u-1/streams-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u-1"))
                .andExpect(jsonPath("$.totalUsdt").value(32000))
                .andExpect(jsonPath("$.breakdown[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.breakdown[1].symbol").value("ETHUSDT"));
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

Run: `mvn -pl portfolio-service test -Dtest=StreamsPortfolioValueControllerTest -q`
Expected: compilation error (`PortfolioValueStoreReader` / `StreamsPortfolioValueController` do not exist).

- [ ] **Step 3: Implement the reader**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueStoreReader.java`:

```java
package ch.unisg.cryptoflow.portfolio.streams;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the latest {@link PortfolioValue} per user from the materialised
 * {@code portfolio-value-store}. Single-instance assumption in dev — for a
 * multi-instance deployment add metadata-lookup-with-redirect using
 * {@code KafkaStreams.queryMetadataForKey(...)}.
 */
@Component
public class PortfolioValueStoreReader {

    private final ObjectProvider<StreamsBuilderFactoryBean> factoryBeanProvider;

    public PortfolioValueStoreReader(ObjectProvider<StreamsBuilderFactoryBean> factoryBeanProvider) {
        this.factoryBeanProvider = factoryBeanProvider;
    }

    public Optional<PortfolioValue> findByUserId(String userId) {
        StreamsBuilderFactoryBean factoryBean = factoryBeanProvider.getIfAvailable();
        KafkaStreams streams = factoryBean == null ? null : factoryBean.getKafkaStreams();
        if (streams == null) {
            return Optional.empty();
        }
        try {
            ReadOnlyKeyValueStore<String, PortfolioValue> store = streams.store(
                    StoreQueryParameters.fromNameAndType(
                            PortfolioValuationStreamConfig.PORTFOLIO_VALUE_STORE,
                            QueryableStoreTypes.keyValueStore()));
            return Optional.ofNullable(store.get(userId));
        } catch (InvalidStateStoreException ignored) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: Implement the controller**

Create `portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/adapter/in/web/StreamsPortfolioValueController.java`:

```java
package ch.unisg.cryptoflow.portfolio.adapter.in.web;

import ch.unisg.cryptoflow.events.avro.PortfolioValue;
import ch.unisg.cryptoflow.events.avro.PositionValue;
import ch.unisg.cryptoflow.portfolio.streams.PortfolioValueStoreReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive-query endpoint for scope-04. Reads the latest
 * {@link PortfolioValue} from the local materialised state store maintained
 * by {@code PortfolioValuationStreamConfig}.
 *
 * <p>Kept distinct from the existing {@code /portfolios/{userId}/value}
 * endpoint (Postgres-backed) so both can be exercised side-by-side for the
 * state-store-vs-Postgres comparison story in the project report.
 */
@RestController
@RequestMapping("/portfolios")
public class StreamsPortfolioValueController {

    private final PortfolioValueStoreReader reader;
    private final ObjectMapper objectMapper;

    public StreamsPortfolioValueController(PortfolioValueStoreReader reader, ObjectMapper objectMapper) {
        this.reader = reader;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{userId}/streams-value")
    public ResponseEntity<Object> getStreamsValue(@PathVariable String userId) {
        return reader.findByUserId(userId)
                .<ResponseEntity<Object>>map(value -> ResponseEntity.ok(toResponse(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toResponse(PortfolioValue value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", value.getUserId());
        body.put("totalUsdt", value.getTotalUsdt());
        body.put("asOf", value.getAsOf());
        List<Map<String, Object>> breakdown = value.getBreakdown().stream()
                .map(this::positionToMap)
                .toList();
        body.put("breakdown", breakdown);
        return body;
    }

    private Map<String, Object> positionToMap(PositionValue pv) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", pv.getSymbol());
        map.put("quantity", pv.getQuantity());
        map.put("valueUsdt", pv.getValueUsdt());
        return map;
    }
}
```

- [ ] **Step 5: Re-run controller test, confirm it passes**

Run: `mvn -pl portfolio-service test -Dtest=StreamsPortfolioValueControllerTest -q`
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/streams/PortfolioValueStoreReader.java \
        portfolio-service/src/main/java/ch/unisg/cryptoflow/portfolio/adapter/in/web/StreamsPortfolioValueController.java \
        portfolio-service/src/test/java/ch/unisg/cryptoflow/portfolio/adapter/in/web/StreamsPortfolioValueControllerTest.java
git commit -m "portfolio-service: expose interactive-query endpoint for scope-04 valuation"
```

---

## Task 7 — Full test suite + Docker smoke test

- [ ] **Step 1: Run the portfolio-service test suite**

Run: `mvn -pl portfolio-service test -q`
Expected: BUILD SUCCESS. All new tests plus existing ones pass.

- [ ] **Step 2: Build the JAR**

Run: `mvn -pl portfolio-service -am -DskipTests package -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Bring the stack up**

Run: `cd docker && docker compose up -d --build portfolio-service kafka schema-registry postgres`
Expected: containers start, `docker compose ps` shows them healthy after ~30 s.

- [ ] **Step 4: Verify the output topic exists**

Run: `docker compose exec kafka kafka-topics --bootstrap-server kafka:29092 --describe --topic portfolio.value.updated`
Expected: topic exists, 3 partitions, `cleanup.policy=compact`.

- [ ] **Step 5: Trigger a buy through transaction-service and observe the output**

Place a small buy order through the transaction-service UI or REST (see existing transaction-service README). Then:

Run: `curl -s http://localhost:8082/portfolios/<userId>/streams-value | jq .`
Expected: a JSON body with `userId`, `totalUsdt > 0`, and a `breakdown` array containing the bought symbol. If the state store has no entry yet, the price tick may not have arrived — wait a few seconds and re-try.

Also tail the output topic for confirmation:
Run: `docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic portfolio.value.updated --from-beginning --max-messages 3 --property print.key=true`
Expected: at least one record keyed by userId with an Avro payload (will print as bytes — use kafka-ui at http://localhost:8080 for a decoded view).

- [ ] **Step 6: Verify the existing `/value` endpoint still works**

Run: `curl -s http://localhost:8082/portfolios/<userId>/value | jq .`
Expected: unchanged Postgres-backed behaviour.

- [ ] **Step 7: Commit (only if smoke test surfaced any fixes)**

If no fixes were needed, skip the commit.

---

## Task 8 — ADR-0034 documenting the scope-04 decisions

**Files:**
- Create: `docs/adrs/0034_portfolio_valuation_streams_app.md`

- [ ] **Step 1: Read the ADR template**

Run: `head -50 /Users/ioannis/Repositories/EDPO/EDPO-Project-FS26/docs/adrs/0000_template_architecture_decisions.md`
Expected: standard MADR-like template with Status/Context/Decision/Consequences.

- [ ] **Step 2: Write the ADR**

Create `docs/adrs/0034_portfolio_valuation_streams_app.md`:

```markdown
# 34. Portfolio valuation as a Kafka Streams app inside portfolio-service

Date: 2026-05-20

## Status

Accepted

## Context

Scope 04 (`docs/event-processes/04-portfolio-valuation.md`) requires
continuously computing per-user portfolio value (sum of `qty × price`) and
exposing it through interactive queries. It must also realise three patterns
the course requires: **Table-Table Join**, **Multiphase Repartitioning** and
**Processing with Local State**.

Three placement questions stood open:

1. *Host* — new `portfolio-valuation-service` module vs inside
   `portfolio-service`.
2. *Holdings source* — replay `transaction.order.approved` vs CDC the
   existing Postgres `holding` table.
3. *Price source* — `crypto.price.raw` (USDT, exists) vs
   `crypto.price.localized` (ADR-0030, not yet built).

A fourth question — processing guarantee — defaulted to `at_least_once`
because all folds are commutative and associative (signed-sum into a
holdings KTable; latest-value table for prices); retries cannot
double-count.

## Decision

- The streams app lives inside `portfolio-service` as a new
  `@Configuration` (`PortfolioValuationStreamConfig`), with its own
  `applicationId` (`portfolio-service-valuation`) so the existing
  `@KafkaListener` consumer group is unaffected.
- Holdings are projected by **replaying `transaction.order.approved`** and
  folding a signed quantity into a KTable keyed by `userId|symbol`. The
  existing Postgres `holding` table stays as a parallel read model
  maintained by the existing `OrderApprovedEventConsumer`.
- The price input is **`crypto.price.raw`** (USDT-denominated). This
  matches the ADR-0028/0031 doctrine of aggregating in USDT and converting
  to Display Currency at API read time. When ADR-0030's
  `crypto.price.localized` ships, this remains the right input for the
  USDT-canonical aggregate; localisation continues to happen on read.
- Processing guarantee: **`at_least_once`** with idempotent folds.
- Two interactive-query surfaces coexist:
  `GET /portfolios/{userId}/value` (existing, Postgres-backed) and
  `GET /portfolios/{userId}/streams-value` (new, state-store-backed). The
  side-by-side comparison is the report deliverable from scope 04.
- Multi-instance metadata-redirect for IQ is **out of scope** — dev runs
  one replica. Documented as a known limitation.

## Consequences

- One topology in portfolio-service realises three of the four required
  patterns in a single user-facing flow (FK Table-Table Join, multiphase
  repartition via `KTable.groupBy(userId)`, and Local State via the
  materialised `portfolio-value-store`).
- Holdings now exist in two places — Postgres (via the existing consumer)
  and the Streams state store. Both are deterministic projections of the
  same `OrderApprovedEvent` topic, so they stay consistent; they cannot
  drift independently because neither writes to the other.
- A fresh `applicationId` triggers an offset-reset rebuild that reads the
  full `transaction.order.approved` and `crypto.price.raw` history from
  the start — that gives us the "reprocessing" demo for free.
- portfolio-service now needs the `kafka-streams` and
  `kafka-streams-avro-serde` dependencies in addition to its existing
  Spring Kafka consumer machinery.
- A future sell flow only needs to change `SignedQuantity.of(...)`; the
  rest of the topology is side-agnostic.
```

- [ ] **Step 3: Commit**

```bash
git add docs/adrs/0034_portfolio_valuation_streams_app.md
git commit -m "docs: add ADR-0034 for scope-04 portfolio valuation streams app"
```

---

## Task 9 — Update scope spec + README

**Files:**
- Modify: `docs/event-processes/04-portfolio-valuation.md`
- Modify: `docs/event-processes/README.md`
- Modify: `README.md`

- [ ] **Step 1: Update scope 04 status and resolve open decisions**

In `docs/event-processes/04-portfolio-valuation.md`:

Replace the heading status line:

Old:
```markdown
**Type:** stateful &nbsp;|&nbsp; **Required patterns:** Stream-Table Join, Table-Table Join, Processing with Local State, Multiphase Repartitioning, Interactive Queries &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** draft
```

New:
```markdown
**Type:** stateful &nbsp;|&nbsp; **Required patterns:** Stream-Table Join, Table-Table Join, Processing with Local State, Multiphase Repartitioning, Interactive Queries &nbsp;|&nbsp; **Owner:** Janni &nbsp;|&nbsp; **Status:** implemented (ADR-0034)
```

Replace the "Open decisions" section (the entire `## Open decisions` block) with:

```markdown
## Open decisions

All resolved by [ADR-0034](../adrs/0034_portfolio_valuation_streams_app.md):

- [x] Processing guarantee → **at_least_once** (folds are commutative and associative; retries cannot double-count).
- [x] Price input → **`crypto.price.raw`** (USDT-canonical; localisation stays at API read time per ADR-0028/0031).
- [x] Emit policy → emit on every change to the user's aggregate (no throttle; downstream consumers see the natural KTable changelog).
- [x] IQ host → **portfolio-service** (`GET /portfolios/{userId}/streams-value`); the existing `/value` Postgres endpoint stays for comparison.
- [x] State store vs Postgres for holdings → **both, parallel projections** of `transaction.order.approved`; the Streams store is authoritative for valuations, Postgres remains the system of record for the holding rows surfaced by the existing UI.
- [x] Signed quantity → centralised in `SignedQuantity.of(OrderApprovedEvent)`; today returns `+amount` (only buys exist). Sell flow only changes this helper.
```

Replace the "ADR candidates" section with:

```markdown
## ADR candidates

Subsumed by [ADR-0034](../adrs/0034_portfolio_valuation_streams_app.md).
```

- [ ] **Step 2: Update the coverage table in `docs/event-processes/README.md`**

In `docs/event-processes/README.md`, in the "Coverage vs the required pattern list" table, append the ADR-0034 mark to the rows that scope 04 covers (Local State, Multiphase, Stream-Table Join, Table-Table Join, Interactive Queries). The simplest change is to replace the table block:

Old:
```markdown
| Required pattern | Scope(s) |
|---|---|
| Single-Event Processing | 1, 2 |
| Processing with Local State | 4, 5, 6 |
| Multiphase Processing / Repartitioning | 4 (table-join → groupBy user), 6 if fed from 5 |
| Stream-Table Join (external lookup) | 3, 4 |
| Table-Table Join | 4 (prices KTable × holdings KTable) |
| Streaming Join | — **gap**, closed by order-book option 3 |
| Out-of-Sequence Events | 5, 6 (grace period) |
| Reprocessing | cross-cutting — commit to demonstrating on 5 or 6 |
| Interactive Queries | 4 (portfolio); optionally 5 (latest candle) |
```

New:
```markdown
| Required pattern | Scope(s) | Shipped? |
|---|---|---|
| Single-Event Processing | 1, 2 | 1 ✅ |
| Processing with Local State | 4, 5, 6 | 4 ✅, 5 ✅ |
| Multiphase Processing / Repartitioning | 4 (table-join → groupBy user) | 4 ✅ |
| Stream-Table Join (external lookup) | 3, 4 (FK), 5 (coin-metadata GlobalKTable) | 4 ✅, 5 ✅ |
| Table-Table Join | 4 (prices KTable × holdings KTable) | 4 ✅ |
| Streaming Join | — **gap**, closed by order-book option 3 | ❌ |
| Out-of-Sequence Events | 5, 6 (grace period) | 5 ✅ |
| Reprocessing | cross-cutting — fresh applicationId rebuilds state from `transaction.order.approved` + `crypto.price.raw` | 4 ✅ |
| Interactive Queries | 4 (portfolio), market-scout dashboard | 4 ✅, scout ✅ |
```

- [ ] **Step 3: Add the topic to the project README**

In `README.md`, locate the existing topic table or Kafka section and add a row/line for `portfolio.value.updated`:

If a topic table exists, add:
```markdown
| `portfolio.value.updated` | compact | userId | `PortfolioValue` (Avro) | portfolio-service scope-04 |
```

If only a narrative exists, insert after the existing `crypto.ohlc.*` mention:
```markdown
`portfolio-service` additionally publishes `portfolio.value.updated` — the
scope-04 valuation KTable changelog — and exposes it through
`GET /portfolios/{userId}/streams-value`.
```

(Choose whichever fits the README structure; do not invent a new table.)

- [ ] **Step 4: Commit**

```bash
git add docs/event-processes/04-portfolio-valuation.md \
        docs/event-processes/README.md \
        README.md
git commit -m "docs: mark scope-04 implemented and resolve open decisions"
```

---

## Self-Review checklist

- [ ] Each of the four required patterns is implemented or already shipped: Single-Event (already ✅), Local State (Task 5 ✅), Multiphase Repartition (Task 5, `KTable.groupBy(userId)` ✅), Stream-Table/Table-Table Join (Task 5 FK join ✅).
- [ ] Interactive Queries pattern is covered by the new endpoint (Task 6).
- [ ] No placeholder text (`TODO`, `TBD`, "implement later") in any task.
- [ ] All file paths are absolute or workspace-relative and exist or are explicitly created.
- [ ] `PortfolioValuationStreamConfig.buildTopology` signature in Task 5 matches what the test in Task 5 calls.
- [ ] `PORTFOLIO_VALUE_STORE` constant referenced from `PortfolioValueStoreReader` (Task 6) matches the constant defined in `PortfolioValuationStreamConfig` (Task 5).
- [ ] No backwards-compatibility shims; existing `/portfolios/{userId}/value` left untouched intentionally for the comparison story.

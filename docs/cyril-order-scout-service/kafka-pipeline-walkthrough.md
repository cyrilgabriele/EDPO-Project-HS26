# Market Scout Kafka Pipeline Walkthrough

This document explains the Market Scout Kafka pipeline from Binance ingestion to the derived scout topics.

## Pipeline Overview

```text
Binance USD-M partial-depth WebSocket
  -> market-partial-book-ingestion-service
  -> RawOrderBookDepthEvent as JSON
  -> crypto.scout.raw
  -> market-order-scout-service Kafka Streams topology
  -> AskQuote as Avro
  -> crypto.scout.ask-quotes
  -> MatchableAsk as Avro
  -> crypto.scout.matchable-asks
  -> threshold filter: ask price <= MARKET_SCOUT_ASK_THRESHOLD
  -> AskOpportunity as Avro
  -> crypto.scout.ask-opportunities
  -> group by symbol + time window
  -> ScoutWindowSummary as Avro
  -> crypto.scout.window-summary
```

## 1. Binance Partial-Depth Subscription

The ingestion service is `market-partial-book-ingestion-service`.

The relevant runtime defaults are defined in:

- `market-partial-book-ingestion-service/src/main/resources/application.yaml`
- `docker/docker-compose.yml`

Current default symbols:

```text
BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT,XRPUSDT,LTCUSDT
```

The service reads these through:

```yaml
binance:
  partial-depth:
    symbols: ${BINANCE_PARTIAL_DEPTH_SYMBOLS:BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT,XRPUSDT,LTCUSDT}
    depth: ${BINANCE_PARTIAL_DEPTH_LEVELS:20}
    update-speed: ${BINANCE_PARTIAL_DEPTH_UPDATE_SPEED:250ms}
```

When the service is started through `docker/docker-compose.yml`, Compose
currently overrides the same environment variable with a `500ms` default:

```yaml
BINANCE_PARTIAL_DEPTH_UPDATE_SPEED: ${BINANCE_PARTIAL_DEPTH_UPDATE_SPEED:-500ms}
```

The WebSocket subscription URL is assembled in:

```text
market-partial-book-ingestion-service/src/main/java/ch/unisg/cryptoflow/marketpartialbookingestion/adapter/in/binance/BinancePartialDepthWebSocketClient.java
```

With the application default, each symbol builds a Binance stream name like:

```text
btcusdt@depth20@250ms
ethusdt@depth20@250ms
ltcusdt@depth20@250ms
```

When a WebSocket text message arrives, the client maps the payload and publishes the resulting raw event to Kafka.

## 2. Binance Payload Mapping

The Binance JSON payload is mapped in:

```text
market-partial-book-ingestion-service/src/main/java/ch/unisg/cryptoflow/marketpartialbookingestion/adapter/in/binance/BinancePartialDepthEventMapper.java
```

It converts Binance fields into the internal raw event:

- `s` -> symbol
- `E` -> Binance event time
- `T` -> Binance transaction time
- `U` -> first update ID
- `u` -> final update ID
- `pu` -> previous final update ID
- `b` -> bid levels
- `a` -> ask levels

The internal raw event type is:

```text
shared-events/src/main/java/ch/unisg/cryptoflow/events/RawOrderBookDepthEvent.java
```

Each bid or ask level is represented by:

```text
shared-events/src/main/java/ch/unisg/cryptoflow/events/OrderBookLevel.java
```

An `OrderBookLevel` contains:

```java
BigDecimal price;
BigDecimal quantity;
```

## 3. Raw Topic Publication

Raw events are published in:

```text
market-partial-book-ingestion-service/src/main/java/ch/unisg/cryptoflow/marketpartialbookingestion/adapter/out/kafka/RawOrderBookDepthKafkaProducer.java
```

The important behavior is:

```java
kafkaTemplate.send(topic, event.symbol(), event)
```

The Kafka message key is the trading symbol. This is intentional because Kafka's default partitioner keeps all records for the same symbol on the same partition, preserving per-symbol ordering.

The raw topic is:

```text
crypto.scout.raw
```

It is created by:

```text
market-partial-book-ingestion-service/src/main/java/ch/unisg/cryptoflow/marketpartialbookingestion/config/KafkaTopicConfig.java
```

The raw topic has three partitions by default:

```yaml
crypto:
  kafka:
    topic:
      scout-raw: crypto.scout.raw
      scout-raw-partitions: 3
```

The raw topic value is JSON. It should be readable in Kafka UI.

## 4. Kafka Streams Consumer

The downstream processor is `market-order-scout-service`.

Kafka Streams is configured in:

```text
market-order-scout-service/src/main/java/ch/unisg/cryptoflow/marketscout/config/MarketScoutStreamsConfig.java
```

The topology itself is defined in:

```text
market-order-scout-service/src/main/java/ch/unisg/cryptoflow/marketscout/adapter/in/kafka/MarketScoutTopology.java
```

The topology consumes:

```text
crypto.scout.raw
```

It uses:

- string key serde
- JSON value serde for `RawOrderBookDepthEvent`
- a custom timestamp extractor

The timestamp extractor is:

```text
market-order-scout-service/src/main/java/ch/unisg/cryptoflow/marketscout/adapter/in/kafka/RawOrderBookDepthTimestampExtractor.java
```

It uses Binance transaction time first. If that is missing, it falls back to Binance event time, then to Kafka record time.

## 5. Raw Event Filter

The first topology filter only keeps usable raw events:

```java
.filter((key, event) -> event != null
        && event.symbol() != null
        && event.asks() != null
        && !event.asks().isEmpty())
```

This step does not apply the price threshold. It only removes null, malformed, or ask-empty events.

## 6. Ask Quote Derivation

Each raw partial-depth event contains multiple ask levels. The topology flattens one raw event into many `AskQuote` records.

This happens in `MarketScoutTopology.toAskQuotes`.

Each `AskQuote` contains:

- symbol
- ask price
- ask quantity
- best ask price
- best ask quantity
- ask notional, computed as `price * quantity`
- Binance transaction time
- Binance event time
- source venue
- local processed time

The resulting stream is written to:

```text
crypto.scout.ask-quotes
```

This is a derived topic and uses binary Avro serialization.

## 7. Matchable Ask Derivation

Every `AskQuote` is also translated into the cross-service `MatchableAsk` contract:

- ask quote id
- symbol
- ask price
- ask quantity
- exchange event time
- source venue

The resulting stream is written to:

```text
crypto.scout.matchable-asks
```

This topic is independent from the scout threshold. It contains ask levels that
can be considered by `transaction-service` for bid matching, even when the ask
would not be counted as an `AskOpportunity`.

## 8. Ask Threshold Filter

The ask threshold is defined in:

```text
market-order-scout-service/src/main/resources/application.yaml
```

Current default:

```yaml
crypto:
  market-scout:
    ask-threshold: ${MARKET_SCOUT_ASK_THRESHOLD:100000.00}
```

So the default threshold is:

```text
100000.00
```

It can be overridden at runtime with:

```text
MARKET_SCOUT_ASK_THRESHOLD
```

The property is injected in:

```text
market-order-scout-service/src/main/java/ch/unisg/cryptoflow/marketscout/config/MarketScoutStreamsConfig.java
```

It is stored in:

```text
market-order-scout-service/src/main/java/ch/unisg/cryptoflow/marketscout/adapter/in/kafka/MarketScoutTopologyProperties.java
```

The actual threshold filter is in `MarketScoutTopology`:

```java
.filter((key, quote) -> quote.getPrice().compareTo(properties.askThreshold()) <= 0)
```

That means the pipeline keeps only ask quotes where:

```text
ask price <= configured threshold
```

The kept quote is mapped into an `AskOpportunity`. The configured threshold is copied into the output event, so consumers can see which threshold produced the opportunity.

The resulting stream is written to:

```text
crypto.scout.ask-opportunities
```

This topic also uses binary Avro serialization.

## 9. Window Summary Aggregation

The opportunity stream is grouped by Kafka key, which is still the symbol:

```java
.groupByKey(...)
```

Then it is windowed:

```java
.windowedBy(TimeWindows.ofSizeWithNoGrace(properties.summaryWindow()))
```

The default window size is defined in:

```text
market-order-scout-service/src/main/resources/application.yaml
```

Default:

```yaml
crypto:
  market-scout:
    window-size: ${MARKET_SCOUT_WINDOW_SIZE:30s}
```

For each symbol and time window, the topology computes:

- opportunity count
- minimum ask price
- window start
- window end

The result is written to:

```text
crypto.scout.window-summary
```

This topic uses binary Avro serialization.

## 10. Derived Topic Creation

The derived topics are created in:

```text
market-order-scout-service/src/main/java/ch/unisg/cryptoflow/marketscout/config/KafkaTopicConfig.java
```

The configured topics are:

```yaml
crypto:
  kafka:
    topic:
      scout-derived-partitions: 3
      scout-ask-quotes: crypto.scout.ask-quotes
      scout-matchable-asks: crypto.scout.matchable-asks
      scout-ask-opportunities: crypto.scout.ask-opportunities
      scout-window-summary: crypto.scout.window-summary
```

Because the topology keeps the symbol as the Kafka key, derived records should follow the same symbol-based partition placement as the raw records, assuming the topic partition count is the same.

## 11. Transaction Matching Consumer

`transaction-service` consumes `crypto.scout.matchable-asks` in its own Kafka
Streams topology. That topology also consumes `transaction.buy-bids`, the topic
written by `PlaceOrderWorker` after a Camunda order form is validated and stored
as a pending transaction.

The matching topology is:

```text
transaction.buy-bids
crypto.scout.matchable-asks
  -> transaction-service Kafka Streams matcher
  -> transaction.order-matched
  -> OrderMatchedEventConsumer
  -> Zeebe message priceMatchedEvent correlated by transactionId
```

The matcher is not a symmetric stream-stream join. It stores pending buy bids by
symbol and tries to allocate a newly arriving `MatchableAsk` to one pending bid.
If a match is produced, the ask quote id and transaction id are recorded in
state stores so the same ask or transaction is not allocated twice.

Current eligibility rules:

```text
ask.eventTime >= bid.createdAt
ask.eventTime <= bid.createdAt + TRANSACTION_MATCHING_VALIDITY_WINDOW
bid.bidPrice >= ask.askPrice
bid.bidQuantity <= ask.askQuantity
```

The default validity window is `30s`, with a `5s` grace period. The BPMN process
uses a `35s` rejection timer so the workflow can wait for the stream match and
still reject orders that do not match.

## 12. Serialization In Kafka UI

The raw topic is JSON:

```text
crypto.scout.raw
```

Kafka UI should show readable JSON values there.

The derived topics are binary Avro:

```text
crypto.scout.ask-quotes
crypto.scout.matchable-asks
crypto.scout.ask-opportunities
crypto.scout.window-summary
```

Kafka UI may show unreadable bytes for those values unless it is configured with the matching Avro schema or a custom decoder. Seeing a readable key like `LTCUSDT` and an unreadable binary value on a derived topic is expected.

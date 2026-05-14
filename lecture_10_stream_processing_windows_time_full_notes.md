# Lecture 10 — Stream Processing: Windows and Time

**Course:** Event-driven and Process-oriented Architectures  
**Institution:** University of St. Gallen, Institute of Computer Science (ICS-HSG)  
**Lecture topic:** Stream Processing — Windows and Time  
**Source:** Uploaded lecture PDF `10-EDPO 2026-05-07.pdf`

---

## 0. Executive Overview

This lecture explains how stream processing systems, especially **Kafka Streams** and **ksqlDB**, handle **time-aware stream processing**.

The central problem is:

> Event streams are unbounded, events may arrive late or out of order, and applications still need correct time-based aggregations, joins, alerts, and queries.

The lecture builds the topic in four major parts:

1. **Relationship of events and time**
   - Event time
   - Ingestion/log append time
   - Processing/wall-clock time
   - Timestamp extraction in Kafka Streams

2. **Windows**
   - Tumbling windows
   - Hopping windows
   - Session windows
   - Sliding join windows
   - Sliding aggregation windows
   - Grace periods
   - Suppression of intermediate results

3. **Kafka Streams Processor API**
   - Difference between the DSL and Processor API
   - When low-level control is needed

4. **ksqlDB**
   - Event streaming database built on Kafka Streams and Kafka Connect
   - SQL interface for streams and tables
   - Push and pull queries
   - Materialized views
   - Interactive and headless deployment modes

The lecture uses a **patient monitoring example** to make the concepts concrete: pulse events and body temperature events are processed, aggregated, joined, and converted into medical alerts.

---

## 1. Agenda

The lecture agenda consists of four topics:

1. Relationship of events and time
2. Introduction into windows
3. Kafka Streams Processor API
4. The event streaming database ksqlDB

These topics are tightly connected. The basic chain is:

```text
Events have timestamps
→ timestamps define time semantics
→ time semantics determine how windows behave
→ windows enable aggregations and joins
→ late events require grace periods
→ suppress controls when final results are emitted
→ Kafka Streams/ksqlDB provide APIs to implement these patterns
```

---

# Part I — Relationship of Events and Time

---

## 2. Why Time Matters in Stream Processing

In ordinary batch processing, data is often processed after it is complete. In stream processing, data is processed continuously while it arrives.

This creates a fundamental challenge:

> The order in which events arrive is not always the same as the order in which they happened.

For example, a medical sensor may produce measurements every second. If the network connection becomes unstable, some older measurements may arrive after newer measurements. The stream processor therefore needs a way to determine the actual time of each event.

Time matters because it determines correctness for:

- Windowed aggregations
- Windowed joins
- Alerting logic
- State expiration
- Late-event handling
- Interactive queries over time ranges

The lecture emphasizes that choosing the correct time semantics is essential for time-aware operations such as **windowed aggregations** and **windowed joins**.

---

## 3. Healthcare Example: Predictive Monitoring

The lecture introduces a healthcare use case from Children's Healthcare of Atlanta.

The system uses Kafka Streams and ksqlDB to make real-time predictions about whether children with head trauma may need surgical intervention soon.

The general idea:

1. Sensors measure medical signals, such as intracranial pressure.
2. Measurements are aggregated over time.
3. Aggregates are sent to a predictive model.
4. If the model predicts that pressure may reach dangerous levels in the next 30 minutes, healthcare professionals are notified.

This motivates why time-aware stream processing matters:

> Medical alerts are only useful if the system interprets the timing of measurements correctly.

If older measurements arrive late and are interpreted as current measurements, the prediction may be wrong. If current measurements are ignored because the system uses the wrong timestamp, the alert may also be wrong.

---

## 4. Example Topology: Patient Monitoring

The lecture uses a simplified topology involving two event streams:

1. `pulse-events`
2. `body-temp-events`

The goal is to detect **systemic inflammatory response syndrome (SIRS)** using vital signs.

Relevant vital signs include:

- Body temperature
- Blood pressure
- Heart rate

The lecture focuses on two measurements:

- Body temperature
- Heart rate

The alerting logic is:

> When both heart rate and body temperature reach predefined thresholds, send a record to an `alerts` topic.

The simplified topology is:

```text
pulse-events                     body-temp-events
    │                                  │
    ▼                                  ▼
groupByKey                         Filter
    │                                  │
    ▼                                  │
Windowed aggregation                  │
    │                                  │
    ▼                                  │
Suppress                              │
    │                                  │
    ▼                                  │
Filter                                │
    │                                  │
    ▼                                  │
Rekey / map                           │
    │                                  │
    └──────────────┬───────────────────┘
                   ▼
              Windowed join
                   │
                   ▼
                alerts
```

There is also an **interactive query** path, where applications can query local state stores.

Important operations in this topology:

- `groupByKey`
- Windowed aggregation
- Suppression
- Filtering
- Rekeying
- Windowed join
- Writing to alerts
- Interactive query over materialized state

---

## 5. Event Examples

### 5.1 Pulse Event

A pulse event may contain a timestamp:

```json
{
  "timestamp": "2020-11-05T09:02:00.000Z"
}
```

The timestamp represents the **event time**, meaning the time at which the event actually occurred.

### 5.2 Body Temperature Event

A body temperature event may look like this:

```json
{
  "timestamp": "2020-11-04T09:02:06.500Z",
  "temperature": 101.2,
  "unit": "F"
}
```

The important point is that the timestamp is embedded in the payload. Kafka Streams must extract this timestamp if it wants to use event-time semantics.

---

## 6. Different Time Semantics

The lecture distinguishes three major kinds of time semantics.

---

### 6.1 Event Time

**Event time** is the time when the event actually occurred at the data source.

Example:

```text
A sensor measures body temperature at 09:02:06.500.
```

That timestamp is the event time, even if the record reaches Kafka later.

Event time may be:

- Embedded in the event payload
- Set directly by the Kafka producer

Event time is usually preferred for correctness because it reflects real-world ordering.

Use event time when:

- You need accurate time-windowed aggregations
- You need correct joins between events that happened around the same time
- Events may arrive late or out of order
- Reprocessing should preserve the original temporal meaning of the data

---

### 6.2 Log Append Time / Ingestion Time

**Log append time**, also called **ingestion time**, is the time when the Kafka broker appends the event to a topic.

Example:

```text
Sensor measures event at 09:02:06.500.
Broker receives and appends it at 09:02:09.000.
```

Here:

```text
Event time     = 09:02:06.500
Ingestion time = 09:02:09.000
```

Ingestion time can be useful when:

- You care about when data entered Kafka
- Event sources do not provide reliable timestamps
- The system is designed around ingestion latency

But ingestion time can distort the actual order of real-world events if messages are delayed before reaching Kafka.

---

### 6.3 Processing Time / Wall-clock Time

**Processing time** is the time when the stream processing application processes the event.

Example:

```text
Sensor measures event at 09:02:06.500.
Kafka broker appends it at 09:02:09.000.
Kafka Streams processes it at 09:02:12.000.
```

Here:

```text
Event time      = 09:02:06.500
Ingestion time  = 09:02:09.000
Processing time = 09:02:12.000
```

Processing time depends on runtime conditions such as:

- Application load
- Consumer lag
- Rebalancing
- Failures
- Reprocessing
- Deployment delays

A major disadvantage is that reprocessing the same data later produces different processing timestamps.

Use processing time when:

- The event source has no useful timestamp
- You intentionally care about when the system processed the event
- You are building operational metrics based on application runtime

However, for domain-correct event processing, event time is usually preferred.

---

## 7. Event-time Processing

In event-time processing, the event source includes a timestamp in each event. This timestamp describes when the event was created or observed at the original source.

On the consuming side, the event processing application must extract the timestamp.

The key benefit is:

> Event-time processing preserves the original order and timing of events as much as possible.

This is essential when late or delayed events occur.

Example:

```text
Event A happens at 09:05:44.
Event B happens at 09:05:45.
```

Even if Event B arrives before Event A, the processor can still use event timestamps to reason about their true temporal order.

---

## 8. Wall-clock Time Processing

Wall-clock time processing ignores, or at least does not rely on, the timestamp from the original event source.

The event processing application may use:

1. The time when the event was created
2. The time when the event was received on the event stream
3. A time derived from one or more event fields
4. The time when the event is read and processed

The lecture contrasts this with event-time processing:

- Event time preserves original timing.
- Wall-clock/processing time depends on when data is processed and may reorder events.

This means that the same data can produce different results if reprocessed later.

---

## 9. Different Time Semantics in Kafka Streams

Kafka Streams can associate records with different kinds of timestamps.

The lecture shows a pipeline with:

```text
Heartbeat sensor
→ Kafka broker
→ Kafka Streams processing application
```

The corresponding timestamps are:

```text
Event created at source        → event time
Event appended to Kafka topic  → ingestion/log append time
Event processed by application → processing time
```

Example event:

```json
{
  "timestamp": "2020-11-12T09:02:00.000Z",
  "sensor": "smart-pulse"
}
```

The timestamp can be embedded in the payload.

Kafka broker/topic configuration can also influence timestamps:

```text
log.message.timestamp.type     // broker level
message.timestamp.type         // topic level
```

Possible configuration values include:

```text
CreateTime
LogAppendTime
```

Important rule:

> Topic-level configuration takes precedence over broker-level configuration.

If you want event-time semantics, you generally want **CreateTime** or a custom timestamp extracted from the payload.

---

## 10. Timestamp Extractors

Kafka Streams uses **TimestampExtractors** to associate each consumed record with a timestamp.

The interface is:

```java
public interface TimestampExtractor {
    long extract(
        ConsumerRecord<Object, Object> record,
        long partitionTime
    );
}
```

The extractor receives:

1. The Kafka `ConsumerRecord`
2. `partitionTime`, which is the most recent timestamp Kafka Streams has seen for the consumed partition

The timestamp extractor is where you control time semantics.

---

### 10.1 Included Timestamp Extractors

Kafka Streams includes several timestamp extractors.

#### FailOnInvalidTimestamp

This extracts the timestamp from the consumer record. That timestamp may represent event time or ingestion time, depending on how the Kafka record timestamp was set.

If the timestamp is invalid, processing fails.

#### LogAndSkipOnInvalidTimestamp

This also extracts from the consumer record, but logs and skips invalid records instead of failing.

#### WallclockTimestampExtractor

This uses wall-clock time and can be used for processing-time semantics.

---

### 10.2 Custom Timestamp Extractor

Custom timestamp extractors are common when event-time semantics are needed and the timestamp is stored in the event payload.

The lecture gives an example called `VitalTimestampExtractor`:

```java
public class VitalTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        Vital measurement = (Vital) record.value();
        if (measurement != null && measurement.getTimestamp() != null) {
            String timestamp = measurement.getTimestamp();
            return Instant.parse(timestamp).toEpochMilli();
        }
        return partitionTime;
    }
}
```

This extractor:

1. Reads the record value.
2. Casts it to a `Vital` measurement.
3. Checks whether it contains a timestamp.
4. Parses the timestamp as an `Instant`.
5. Converts it to epoch milliseconds.
6. Falls back to `partitionTime` if no timestamp can be extracted.

---

### 10.3 Strategies for Missing or Invalid Timestamps

If no timestamp can be extracted, there are several possible strategies:

1. Throw an exception and stop processing.
2. Fall back to `partitionTime`.
3. Return a negative timestamp.

Each choice has different consequences.

#### Throw an exception

This is strict and makes data-quality problems visible immediately. However, one bad record can stop processing.

#### Fall back to `partitionTime`

This keeps the application running, but may assign a timestamp that does not reflect the real event time.

#### Return a negative timestamp

Kafka Streams treats negative timestamps as invalid in many contexts, so this usually indicates an unusable record.

---

## 11. Registering Streams with a Timestamp Extractor

The lecture shows how to register a custom timestamp extractor using `Consumed.withTimestampExtractor`.

Example for pulse events:

```java
StreamsBuilder builder = new StreamsBuilder();

Consumed<String, Pulse> pulseConsumerOptions =
    Consumed.with(Serdes.String(), JsonSerdes.Pulse())
        .withTimestampExtractor(new VitalTimestampExtractor());

KStream<String, Pulse> pulseEvents =
    builder.stream("pulse-events", pulseConsumerOptions);
```

Example for body temperature events:

```java
Consumed<String, BodyTemp> bodyTempConsumerOptions =
    Consumed.with(Serdes.String(), JsonSerdes.BodyTemp())
        .withTimestampExtractor(new VitalTimestampExtractor());

KStream<String, BodyTemp> tempEvents =
    builder.stream("body-temp-events", bodyTempConsumerOptions);
```

The main idea:

> Tell Kafka Streams how to extract the event-time timestamp from each event stream.

This must be done before windowed operations if you want correct event-time behavior.

---

# Part II — Introduction into Windows

---

## 12. Why Windows Are Needed

Streams are unbounded. They do not have a natural end.

That means this question is problematic:

```text
How many pulse events does patient-1 have?
```

Without a time boundary, the answer changes forever.

A better question is:

```text
How many pulse events does patient-1 have in this 60-second interval?
```

This is what windows provide.

> A window is a finite time interval used to group events from an infinite stream.

Windows define how events are grouped in time.

With event-time semantics, windows group records that **occurred around the same time**.

With processing-time semantics, windows group records that were **processed around the same time**.

The choice of time semantics changes the meaning of the window.

---

## 13. Window Types

The lecture introduces five window types:

1. Tumbling windows
2. Hopping windows
3. Session windows
4. Sliding join windows
5. Sliding aggregation windows

The choice of window type affects the result.

A wrong window type can produce technically valid but semantically wrong results.

---

## 14. Tumbling Windows

### 14.1 Definition

Tumbling windows are fixed-size windows that never overlap.

They have a single property:

```text
window size
```

Example:

```text
Window size = 5 seconds
```

The timeline is divided into fixed buckets:

```text
[0, 5) [5, 10) [10, 15) [15, 20) ...
```

Each event belongs to exactly one window.

The lecture describes tumbling windows as fixed time buckets.

---

### 14.2 Tumbling Window Example

Suppose the window size is 5 seconds.

```text
Time: 0        5        10       15
      |--------|--------|--------|
      window 1 window 2 window 3
```

An event at timestamp `3` belongs to the first window.

An event at timestamp `7` belongs to the second window.

An event at timestamp `12` belongs to the third window.

---

### 14.3 Tumbling Window in Kafka Streams

The lecture shows:

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5));
```

This creates fixed-size, non-overlapping windows of 5 seconds with no grace period.

A 60-second tumbling window for pulse-to-BPM conversion would be:

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60));
```

---

### 14.4 When to Use Tumbling Windows

Use tumbling windows when you need fixed, non-overlapping intervals.

Examples:

- Count events per minute
- Compute average temperature per hour
- Count clicks per 10-second interval
- Compute heart beats per 60-second interval

In the lecture, tumbling windows are appropriate for converting raw pulse events into heart rate because heart rate can be computed over a fixed 60-second bucket.

---

## 15. Hopping Windows

### 15.1 Definition

Hopping windows are fixed-size windows that may overlap.

They have two properties:

1. Window size
2. Advance interval

Example:

```text
Window size = 5 seconds
Advance interval = 4 seconds
```

This means a new window starts every 4 seconds, but each window covers 5 seconds.

Because the window size is larger than the advance interval, the windows overlap.

---

### 15.2 Hopping Window Example

Suppose:

```text
window size = 5
advance = 4
```

Then windows may be:

```text
[0, 5)
[4, 9)
[8, 13)
[12, 17)
```

An event at timestamp `4.5` belongs to both:

```text
[0, 5)
[4, 9)
```

So the same event can contribute to multiple window results.

---

### 15.3 Hopping Window in Kafka Streams

The lecture shows:

```java
TimeWindows hoppingWindow = TimeWindows
    .ofSizeWithNoGrace(Duration.ofSeconds(5))
    .advanceBy(Duration.ofSeconds(4));
```

---

### 15.4 When to Use Hopping Windows

Use hopping windows when you want a rolling or overlapping metric.

Examples:

- Count clicks over the last 60 seconds, updated every 10 seconds
- Compute moving average over 5 minutes, updated every 1 minute
- Detect rising temperature trend over overlapping intervals

Hopping windows produce more results than tumbling windows because each event can be included in multiple windows.

---

## 16. Session Windows

### 16.1 Definition

Session windows are variable-size windows determined by periods of activity followed by gaps of inactivity.

They have one main parameter:

```text
inactivity gap
```

Unlike tumbling and hopping windows, session windows are not aligned to fixed clock boundaries.

Instead, records define the window boundaries.

---

### 16.2 Session Window Example

Suppose the inactivity gap is 5 seconds.

Events for one key arrive at:

```text
1s, 2s, 4s, 10s, 12s
```

Between 4s and 10s, there is a gap of 6 seconds.

Since the inactivity gap is 5 seconds, this creates two sessions:

```text
Session 1: 1s, 2s, 4s
Session 2: 10s, 12s
```

---

### 16.3 Session Window in Kafka Streams

The lecture shows:

```java
SessionWindows sessionWindow = SessionWindows
    .ofInactivityGapWithNoGrace(Duration.ofSeconds(5));
```

---

### 16.4 When to Use Session Windows

Use session windows when windows should be based on activity patterns rather than fixed time buckets.

Examples:

- User browsing sessions
- Bursts of IoT sensor activity
- Conversation sessions
- Trading activity bursts
- Continuous interaction periods separated by inactivity

For converting pulse events into heart rate, session windows are not appropriate because heart rate requires a fixed measurement interval, such as 60 seconds.

---

## 17. Sliding Join Windows

### 17.1 Definition

Sliding join windows are fixed-size windows used for joins.

Two records are joined if:

```text
same key AND timestamp difference ≤ window size
```

The lecture states:

> Join if events happen close in time.

Sliding join windows are created using the `JoinWindows` class.

---

### 17.2 Sliding Join Example

Suppose the join window is 5 seconds.

Pulse stream:

```text
patient-1, high pulse, timestamp = 10
```

Temperature stream:

```text
patient-1, high temperature, timestamp = 12
```

Timestamp difference:

```text
|12 - 10| = 2 seconds
```

Since 2 ≤ 5, the records join.

Now suppose:

```text
pulse timestamp = 10
temperature timestamp = 17
```

Timestamp difference:

```text
|17 - 10| = 7 seconds
```

Since 7 > 5, the records do not join.

---

### 17.3 Sliding Join Window in Kafka Streams

The lecture shows:

```java
JoinWindows joinWindow = JoinWindows
    .ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5));
```

A later example with grace period uses:

```java
JoinWindows joinWindows =
    JoinWindows.ofTimeDifferenceAndGrace(
        Duration.ofSeconds(60),
        Duration.ofSeconds(10)
    );
```

This means:

- Join records whose timestamps are at most 60 seconds apart.
- Tolerate delayed arrivals up to 10 seconds.

---

### 17.4 When to Use Sliding Join Windows

Use sliding join windows for stream-stream joins where events from two streams should be combined only if they occur close in time.

Examples:

- High pulse + high temperature within 60 seconds
- Click + page view within 5 seconds
- Search + click within 30 seconds
- Payment authorization + transaction confirmation within 2 minutes

---

## 18. Sliding Aggregation Windows

### 18.1 Definition

Sliding aggregation windows are used for fine-grained aggregations over records whose timestamps are close to each other.

The lecture describes them as:

- No fixed buckets
- Window defined per record
- Events grouped if their timestamps are close within the window size
- Continuous/fine-grained aggregation

---

### 18.2 Sliding Aggregation Window in Kafka Streams

The lecture shows:

```java
SlidingWindows slidingWindow =
    SlidingWindows.ofTimeDifferenceAndGrace(
        Duration.ofSeconds(5),
        Duration.ofSeconds(0)
    );
```

This means records are grouped if they occur within a 5-second time difference, with no additional grace period.

---

### 18.3 When to Use Sliding Aggregation Windows

Use sliding aggregation windows when you need continuous, fine-grained aggregation based on temporal proximity, rather than fixed buckets.

Examples:

- Detect clusters of events that happen close together
- Continuously aggregate records within a short moving time difference
- Fine-grained anomaly detection

---

## 19. Selecting the Right Window

The lecture asks:

> Which window type is appropriate to convert raw pulse events into heart rate using a windowed aggregation?

The answer is a **tumbling window**.

Reasoning:

- Tumbling windows provide fixed 60-second buckets.
- Hopping windows overlap, which is unnecessary for simple BPM conversion.
- Session windows are variable-sized, which is not appropriate for fixed heart-rate measurement.
- Sliding join windows are for joins only.
- Sliding aggregation windows do not provide fixed buckets.

Kafka Streams code:

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60));
```

Mental rule:

```text
Fixed non-overlapping interval needed?  → Tumbling window
Fixed overlapping interval needed?      → Hopping window
Activity-based grouping needed?         → Session window
Join two streams by temporal closeness? → Sliding join window
Continuous close-in-time aggregation?   → Sliding aggregation window
```

---

## 20. Window Aggregations

The lecture shows a windowed aggregation that counts pulse events per key and window:

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60));

KTable<Windowed<String>, Long> pulseCounts =
    pulseEvents
        .groupByKey()
        .windowedBy(tumblingWindow)
        .count(Materialized.as("pulse-counts"));

pulseCounts
    .toStream()
    .print(Printed.<Windowed<String>, Long>toSysOut().withLabel("pulse-counts"));
```

Important observations:

1. The input stream is grouped by key.
2. The grouped stream is divided into 60-second tumbling windows.
3. Kafka Streams counts records per key and window.
4. The result is a `KTable`, not just a `KStream`.
5. The key type changes from `String` to `Windowed<String>`.
6. The aggregation is materialized as a local state store named `pulse-counts`.
7. Results are continuously updated as new events arrive.

---

## 21. Materialized Stores

A materialized store is a local, queryable state store that holds the current result of a stateful stream computation.

In the lecture example:

```java
.count(Materialized.as("pulse-counts"))
```

This creates or names a state store called `pulse-counts`.

The store maintains counts like:

```text
(patient-1, window 10:00-10:01) → 72
(patient-2, window 10:00-10:01) → 65
(patient-1, window 10:01-10:02) → 75
```

Materialization is necessary because stateful operations need memory.

Examples of operations that need materialized state:

- Count
- Aggregate
- Reduce
- Windowed aggregation
- Stream-stream join
- Stream-table join
- Table-table join
- Interactive queries

Stateless operations usually do not need materialized stores:

- `map`
- `filter`
- `flatMap`
- `selectKey`, unless it causes repartitioning later

Materialized stores are especially important for:

1. Maintaining aggregation state
2. Supporting joins
3. Supporting interactive queries
4. Building materialized views in ksqlDB

---

## 22. Emitting Window Results

Windowed aggregations raise an important question:

> When should results be emitted?

This is more complex than it first appears because streams are unbounded and events may not arrive in timestamp order.

Kafka guarantees order per partition by offset, not by event time.

That means:

```text
Arrival order ≠ event-time order
```

Example:

```text
Arrival order: 10:00:05, 10:00:20, 10:00:50, 10:00:30
Event-time order: 10:00:05, 10:00:20, 10:00:30, 10:00:50
```

The event with timestamp `10:00:30` arrived late.

This creates a tradeoff:

```text
Wait longer → more complete results
Emit earlier → lower latency
```

Key takeaway:

> Results may change over time as late events arrive.

---

## 23. Out-of-Order Events

Out-of-order events are common in distributed systems.

The lecture gives examples such as:

- Multiple producers send events to the same topic.
- Events from different patients interleave unpredictably.
- Intermittent network issues delay events.
- IoT devices lose connection and later send buffered data.

Example event timestamps:

```text
5:01  5:02  5:03  4:45  4:46  4:47  5:04  5:05
```

Here, older events from `4:45`, `4:46`, and `4:47` arrive after events from `5:01`, `5:02`, and `5:03`.

This requires the application to:

1. Recognize that an event is out of order by examining event time.
2. Reconcile out-of-sequence events during a grace period.

---

## 24. Grace Period

A grace period defines how long Kafka Streams waits for late events before closing a window.

The lecture states:

> Kafka Streams lets you define how long to wait for late events.

Example:

```java
TimeWindows tumblingWindow =
    TimeWindows.ofSizeAndGrace(
        Duration.ofSeconds(60),
        Duration.ofSeconds(5)
    );
```

This means:

- Window size: 60 seconds
- Grace period: 5 seconds

A late event can still be included if it arrives within the grace period.

---

### 24.1 Grace Period Tradeoff

Longer grace period:

```text
+ More complete results
- Higher latency
- More state retained
- More memory/storage pressure
```

Shorter grace period:

```text
+ Lower latency
+ Less state retained
- Higher chance of dropping or ignoring late events
- Less complete results
```

Mental model:

> Grace period is the correctness buffer for out-of-order event-time processing.

Other systems, such as Apache Flink, use **watermarks** for a related purpose.

---

## 25. Suppressed Event Aggregator

The lecture introduces the **Suppressed Event Aggregator** pattern.

It provides final aggregation results while omitting intermediate results.

It requires:

- A grouped stream as input
- Windows based on timestamps
- Buffering of intermediate results
- Emission of a single final event when the window closes

In a normal aggregation, the result may be emitted repeatedly:

```text
count = 1
count = 2
count = 3
count = 4
```

With suppression, only the final result is emitted:

```text
count = 4
```

---

## 26. Why Suppress Is Needed in the Patient Monitoring Example

For heart rate, the application needs a full 60-second window.

If the application emits intermediate values, it may produce misleading results:

```text
After 10 seconds: 12 beats → incomplete
After 30 seconds: 35 beats → incomplete
After 60 seconds: 72 beats → final
```

Therefore, the lecture says:

> Heart rate requires a full 60-second window, so only final results should be emitted using the suppress operator.

---

## 27. Suppress in Kafka Streams

The lecture shows:

```java
TimeWindows tumblingWindow =
    TimeWindows
        .ofSizeAndGrace(
            Duration.ofSeconds(60),
            Duration.ofSeconds(5)
        );

KTable<Windowed<String>, Long> pulseCounts =
    pulseEvents
        .groupByKey()
        .windowedBy(tumblingWindow)
        .count(Materialized.as("pulse-counts"))
        .suppress(
            Suppressed.untilWindowCloses(
                BufferConfig.unbounded().shutDownWhenFull()
            )
        );
```

This means:

1. Use 60-second windows.
2. Allow 5 seconds of grace.
3. Count pulse events by key and window.
4. Materialize counts in a state store.
5. Suppress intermediate updates.
6. Emit only when the window closes.

Important cost:

> Suppression requires buffering, which consumes memory.

The application must define a strategy for buffer overflow.

---

## 28. Rekeying After Windowing

After windowed aggregation, the key changes.

Before windowing:

```text
String key = patientId
```

After windowed aggregation:

```text
Windowed<String> key = patientId + time window
```

This matters because the pulse stream later needs to be joined with the body-temperature stream.

If the body-temperature stream is keyed by patient ID, but the pulse stream is keyed by `Windowed<String>`, the keys no longer match.

Therefore, the pulse stream may need to be rekeyed.

The lecture notes:

> Rekeying triggers repartitioning, which is expensive.

Practical advice:

> Filter first to reduce data volume before rekeying.

This means:

```text
Bad:
rekey everything → filter later

Better:
filter irrelevant records → rekey only smaller relevant stream
```

---

## 29. Logical AND as Windowed Join Across Streams

The lecture describes a pattern called **Logical AND**.

It combines information from several event streams.

Properties:

- Several input streams
- One output stream
- Streams joined based on a common key
- Requires a time window
- Events on both sides must arrive within the window

In the patient monitoring example:

```text
high pulse AND high body temperature → alert
```

This is a logical AND across streams.

Both conditions must be true:

1. Same patient
2. High pulse event
3. High temperature event
4. Events occur close enough in time

---

## 30. Windowed Joins

Windowed joins are required for `KStream`-`KStream` joins because streams are unbounded.

Kafka Streams cannot join an infinite history of one stream with an infinite history of another stream without constraints.

Therefore:

> KStream-KStream joins must be windowed.

The window limits how far back or forward the processor looks for matching records.

---

### 30.1 How Windowed Joins Work

A stream-stream join compares timestamps on both sides.

For a join to happen:

```text
keys must match
AND
timestamps must fall within the join window
```

Kafka Streams materializes local state stores to keep recent records from both streams.

When a new record arrives, Kafka Streams checks the other stream's local state for matching records.

---

### 30.2 Why State Stores Are Needed for Joins

Suppose a pulse event arrives first:

```text
patient-1 high pulse at 10:00:00
```

The matching temperature event may arrive later:

```text
patient-1 high temperature at 10:00:45
```

Kafka Streams must remember the pulse event long enough to join it with the temperature event.

This is why stream-stream joins require local state.

---

## 31. Windowed Join Code Example

The lecture shows:

```java
StreamJoined<String, Long, BodyTemp> joinParams =
    StreamJoined.with(
        Serdes.String(),
        Serdes.Long(),
        JsonSerdes.BodyTemp()
    );

JoinWindows joinWindows =
    JoinWindows.ofTimeDifferenceAndGrace(
        Duration.ofSeconds(60),
        Duration.ofSeconds(10)
    );

ValueJoiner<Long, BodyTemp, CombinedVitals> valueJoiner =
    (pulseRate, bodyTemp) ->
        new CombinedVitals(pulseRate.intValue(), bodyTemp);

KStream<String, CombinedVitals> vitalsJoined =
    highPulse.join(
        highTemp,
        valueJoiner,
        joinWindows,
        joinParams
    );
```

Explanation:

### `StreamJoined`

Defines SerDes for the join:

- Key serde: `String`
- Left value serde: `Long`
- Right value serde: `BodyTemp`

### `JoinWindows`

Defines the time condition:

```text
Records at most 60 seconds apart can join.
Late events are tolerated up to 10 seconds.
```

### `ValueJoiner`

Defines how values are combined:

```text
pulse rate + body temperature → CombinedVitals
```

### `join`

Performs the actual stream-stream join.

---

## 32. Streaming Join Design Pattern

The lecture describes the general streaming join pattern:

> When joining two streams, we conceptually try to match events in one stream with events in the other stream that have the same key and happened in the same time window.

Example:

```text
Clicks stream
Searches stream
```

The join could answer:

```text
Which search event led to which click event within 5 seconds?
```

Kafka Streams maintains local state for both streams and uses a time window to decide which events can be joined.

---

## 33. Time-driven Data Flow

The lecture states:

> Time controls processing order, not arrival order.

Kafka Streams synchronizes input streams and processes the record with the lowest timestamp first, where possible.

This helps produce correct results for joins and aggregations.

The diagram shows:

- Multiple partitions
- Head records compared
- Records sorted by timestamp
- `PartitionGroup.nextRecord`
- One partition group per stream task

The key idea:

```text
processing order = timestamp order
```

within the constraints of Kafka Streams tasks and partition groups.

This is another reason timestamp extraction matters.

If timestamps are wrong, Kafka Streams' time-driven data flow will also be wrong.

---

## 34. Alerts Sink

After the windowed join produces combined vital signs, the results are written to an output topic:

```text
alerts
```

The lecture notes that output records keep a timestamp.

For joins:

```text
timestamp = max(input timestamps)
```

Example:

```text
Pulse event timestamp       = 10:00:00
Temperature event timestamp = 10:00:45
Joined alert timestamp      = 10:00:45
```

The output event's timestamp reflects the later of the two joined input events.

---

## 35. Querying Windowed Stores

Windowed stores use keys that include both:

1. Original key
2. Time window

For example:

```text
(patient-1, window 10:00-10:01)
```

The lecture describes several query types:

1. Lookup by key within a time range
2. Scan keys within a time range
3. Iterate over all entries

For key-based queries, a time range is required.

Why?

Because the key alone is not enough.

```text
patient-1
```

could exist in many windows:

```text
patient-1, 10:00-10:01
patient-1, 10:01-10:02
patient-1, 10:02-10:03
```

Therefore, queries must specify a time range to know which windowed entries to retrieve.

---

# Part III — Kafka Streams Processing API and ksqlDB

---

## 36. Kafka Streams DSL vs Processor API

Kafka Streams applications can be written using two API levels:

1. Kafka Streams DSL
2. Processor API

---

## 37. Kafka Streams DSL

The DSL is a high-level API.

It provides common event processors out of the box.

Examples:

```java
filter(...)
map(...)
groupByKey(...)
windowedBy(...)
count(...)
join(...)
```

The course mostly focuses on the DSL.

Mental model:

> DSL = convenient high-level building blocks for common stream processing tasks.

The lecture summarizes it as suitable for about 90% of use cases.

---

## 38. Processor API

The Processor API is a low-level API.

It allows developers to:

- Add and connect processors manually
- Interact directly with state stores
- Access record metadata
- Schedule periodic functions
- Control exactly when records are forwarded

Mental model:

> Processor API = lower-level control over the processing topology.

---

## 39. When to Use Processor API

Use the Processor API when the DSL is not expressive enough.

Advantages:

1. Access to record metadata
   - Topic
   - Partition
   - Offset
   - Headers

2. Ability to schedule periodic functions

3. Fine-grained control over forwarding records

4. Direct and flexible access to state stores

5. Ability to work around DSL limitations

---

## 40. Disadvantages of Processor API

The lecture also highlights disadvantages:

1. More verbose code
2. Harder to read
3. Harder to maintain
4. Higher barrier to entry
5. More error-prone
6. Risk of reinventing DSL functionality poorly
7. Possible performance pitfalls

Rule:

```text
Use DSL when it is expressive enough.
Use Processor API when you need full control.
```

---

# Part IV — ksqlDB

---

## 41. What Is ksqlDB?

ksqlDB is an event streaming database released by Confluent in 2017.

It integrates:

- Kafka Streams
- Kafka Connect
- SQL interface

It allows developers to model and process streaming data using SQL.

---

## 42. What ksqlDB Provides

ksqlDB allows users to:

1. Model data as streams or tables using SQL
2. Join, aggregate, filter, transform, and window streams/tables
3. Create derived representations of data
4. Use push queries
5. Use pull queries
6. Create materialized views
7. Define connectors to external data sources
8. Combine stream processing and data integration

---

## 43. Streams and Tables in ksqlDB

ksqlDB models data as either streams or tables.

### Stream

A stream is an unbounded sequence of events.

Example:

```text
Each payment transaction is an event.
Each click is an event.
Each sensor measurement is an event.
```

A stream is append-oriented.

### Table

A table represents the latest state for each key.

Example:

```text
customer_id → latest customer profile
product_id → current inventory count
patient_id → latest vital status
```

Tables are often materialized from streams.

---

## 44. Push Queries

A push query runs continuously and emits new results when new data arrives.

Example conceptual query:

```sql
SELECT *
FROM alerts
EMIT CHANGES;
```

This is useful for event-driven microservices because new outputs are pushed as events occur.

Use push queries when:

- You want continuous results
- You want to subscribe to changes
- You want new events as they are produced

---

## 45. Pull Queries

A pull query asks for the current state of a materialized view.

Example conceptual query:

```sql
SELECT *
FROM pulse_counts
WHERE patient_id = 'patient-1';
```

Use pull queries when:

- You want the current value for a key
- You want to query a materialized table/view
- You do not need a continuous stream of changes

---

## 46. Materialized Views in ksqlDB

A materialized view is a continuously maintained query result.

Example conceptual definition:

```sql
CREATE TABLE pulse_counts AS
SELECT patient_id, COUNT(*) AS count
FROM pulse_events
WINDOW TUMBLING (SIZE 60 SECONDS)
GROUP BY patient_id;
```

The result is continuously updated as new events arrive.

This is similar in concept to Kafka Streams materialized stores.

Mental model:

```text
Kafka Streams materialized store ≈ ksqlDB materialized view
```

Both represent maintained state derived from event streams.

---

## 47. When to Use ksqlDB

The lecture lists several reasons to use ksqlDB:

1. More interactive workflows through CLI and REST service
2. Less code to maintain
3. Stream processing topologies expressed in SQL instead of JVM code
4. Lower barrier to entry for developers familiar with SQL
5. Simplified architecture
6. Connector management and stream transformation combined
7. Increased developer productivity
8. Hidden complexity of lower-level systems
9. Cross-project consistency
10. Easy setup and turnkey deployments
11. Better support for data exploration

Rule of thumb:

> Use ksqlDB when the problem can be expressed naturally in SQL.

---

## 48. ksqlDB for Data Integration and Transformation

The lecture explains that ksqlDB supports both:

1. Data integration
2. Data transformation

It integrates with Kafka Connect.

Architecture idea:

```text
Source systems
→ source connectors
→ Kafka / ksqlDB
→ SQL stream/table processing
→ sink connectors
→ external systems
```

ksqlDB extends classical SQL to support streams.

It supports both:

- Push queries
- Pull queries

The key point:

> ksqlDB combines ETL and stream processing.

---

## 49. ksqlDB Architecture

The lecture identifies several architecture components.

---

### 49.1 ksqlDB Server

The ksqlDB server is conceptually similar to an instance of a Kafka Streams application.

Like Kafka Streams applications, ksqlDB servers are deployed independently of the Kafka cluster.

Multiple ksqlDB servers can be organized into ksqlDB clusters.

---

### 49.2 SQL Engine

The SQL engine is responsible for:

1. Parsing SQL statements
2. Translating SQL into Kafka Streams topologies
3. Running those topologies

This is the conceptual bridge:

```text
SQL query → Kafka Streams topology
```

---

### 49.3 REST Service

The REST service allows clients to interact with the SQL engine.

Clients can submit queries and commands through the REST interface.

---

### 49.4 ksqlDB Clients

ksqlDB clients include:

- ksqlDB CLI
- ksqlDB UI

These clients interact with ksqlDB servers, which then manage SQL execution and stream processing.

---

## 50. Deployment Modes: Interactive vs Headless

ksqlDB has two main deployment modes:

1. Interactive mode
2. Headless mode

---

### 50.1 Interactive Mode

Interactive mode is used for:

- Exploration
- Development
- Ad-hoc queries
- Experimentation
- Data discovery

In interactive mode, users can submit queries through:

- CLI
- UI
- REST API

The lecture associates interactive mode with exploratory and ad-hoc workflows.

---

### 50.2 Headless Mode

Headless mode is used for production deployments with predefined queries.

Queries are usually provided in a query file, such as:

```text
query.sql
```

The server runs those predefined queries.

The lecture associates headless mode with production workloads.

Mental rule:

```text
Interactive mode → exploratory/ad-hoc queries
Headless mode    → production/predefined queries
```

---

## 51. Kafka Streams vs ksqlDB

The lecture compares Kafka Streams and ksqlDB.

### Kafka Streams

Characteristics:

- Java/code-based
- Maximum flexibility
- Suitable for complex logic
- Good for custom processing
- Gives more control

Use Kafka Streams when:

- You need complex business logic
- SQL is not expressive enough
- You need custom state handling
- You need low-level control
- You need Processor API capabilities

---

### ksqlDB

Characteristics:

- SQL-based
- Fast development
- Good for standard transformations
- Useful for joins, aggregations, filters, and windowing
- Lower barrier for SQL users

Use ksqlDB when:

- The problem fits naturally into SQL
- You want rapid development
- You want fewer lines of code
- You want easier data exploration
- You want to combine Kafka Connect integration and stream transformations

---

### Main Rule of Thumb

```text
Use ksqlDB if it fits.
Use Kafka Streams if you need more control.
```

---

# Part V — Labs and Project Relevance

---

## 52. Labs Mentioned in the Lecture

The lecture points to several labs.

### 52.1 Patient Monitoring App

For Kafka Streams stateful processing:

```text
lab14Part1-kafka-streams-patientmonitoring
```

This lab relates directly to the patient monitoring example in the lecture.

Concepts involved:

- Stateful processing
- Windowed aggregation
- Suppression
- Windowed joins
- Alerts
- Interactive queries

---

### 52.2 Eye-Tracking Events Processing App

For Kafka Streams stateless and stateful event processing with time windowing:

```text
lab14Part2-kafka-streams-eyeTracking3
```

The lecture shows an eye-tracking domain example with:

- Fixation events
- Click events
- Content filtering
- Event filtering
- Windowed aggregation
- Windowed join
- Interactive queries
- State stores

---

## 53. Eye-Tracking Topology

The topology includes:

```text
Fixation events
→ Content filter
→ Event filter
→ Map fixations to Areas of Interest (AOIs)
→ Windowed aggregation
→ Store: fixationStats
```

Click events follow another path:

```text
Click events
→ Event translator
→ Windowed aggregation
→ Store: clickCount
```

Then both are joined:

```text
fixationStats + clickCount
→ Windowed join
→ Store: FixationClickStats
→ Interactive queries
```

This example reinforces the same core ideas:

- Multiple streams
- Windowed aggregation
- Windowed join
- Materialized stores
- Interactive queries

---

## 54. ksqlDB Credit Card Transactions App

For ksqlDB, the lecture points to a credit card transactions app:

```text
lab15-ksqldb-credittransactions
```

It also references a tutorial on event-driven microservices with ksqlDB for checking suspicious credit card transactions.

Likely concepts:

- Stream creation
- Filtering suspicious transactions
- Joining transaction data
- Materialized views
- Push queries for suspicious activity
- Pull queries for current state

---

## 55. Project Requirements Mentioned

The lecture connects the content to the course project.

Project requirements include:

1. Develop one or several stream processing applications.
2. Include both stateless and stateful stream processing.
3. Include several stateless operations.
4. Use both streams and tables.
5. Consider data from more than one stream.
6. Use interactive queries.
7. Use windowed operations.
8. Describe each topology graphically.
9. Elaborate on chosen patterns.
10. Use Avro schema, either registryless or with schema registry.

Lecture 10 is especially relevant because it adds:

- Windowed operations
- Time-aware processing
- Grace periods
- Suppression
- Windowed joins

These are required or highly relevant for a complete stream-processing project.

---

# Part VI — Conceptual Cheat Sheet

---

## 56. Core Concepts and Their Meanings

| Concept | Meaning | Use Case |
|---|---|---|
| Event time | Time when the event actually happened | Correct domain-time processing |
| Ingestion time | Time when Kafka broker appended the event | Broker arrival-based processing |
| Processing time | Time when application processed event | Runtime/wall-clock processing |
| Timestamp extractor | Component that assigns timestamp to each record | Event-time extraction from payload |
| Window | Finite time group over infinite stream | Aggregations and joins |
| Tumbling window | Fixed, non-overlapping window | Count per minute |
| Hopping window | Fixed, overlapping window | Moving/rolling metrics |
| Session window | Activity-based variable window | User sessions |
| Sliding join window | Join if records are close in time | Stream-stream joins |
| Sliding aggregation window | Fine-grained close-in-time aggregation | Continuous aggregation |
| Grace period | How long to accept late events | Out-of-order handling |
| Suppress | Emit only final window result | Avoid intermediate updates |
| Materialized store | Local queryable state | Aggregations, joins, interactive queries |
| Interactive query | Query local state store | Read current computed results |
| Processor API | Low-level stream processing API | Custom control |
| ksqlDB | SQL interface for Kafka Streams/Kafka Connect | SQL-based stream processing |
| Push query | Continuous query emitting updates | Event-driven output |
| Pull query | Query current materialized state | Lookup current values |

---

## 57. Decision Guide for Window Types

| Goal | Best Window Type | Reason |
|---|---|---|
| Count events per fixed interval | Tumbling | Fixed, non-overlapping buckets |
| Rolling metric updated periodically | Hopping | Fixed overlapping buckets |
| Group bursts of activity | Session | Based on inactivity gaps |
| Join two streams by time proximity | Sliding join | Designed for stream-stream joins |
| Fine-grained continuous aggregation | Sliding aggregation | No fixed buckets, close-in-time grouping |

---

## 58. Decision Guide for Time Semantics

| Situation | Prefer |
|---|---|
| Event contains reliable source timestamp | Event time |
| Source timestamp unavailable but broker time acceptable | Ingestion time |
| You care about when app processes data | Processing time |
| Reprocessing should produce same temporal results | Event time |
| Late/out-of-order events matter | Event time + grace period |

---

## 59. Decision Guide for APIs

| Need | Use |
|---|---|
| Standard filtering, mapping, joining, aggregation | Kafka Streams DSL |
| Full control over metadata, forwarding, scheduling, state stores | Processor API |
| SQL-based stream processing | ksqlDB |
| Fast exploration and ad-hoc queries | ksqlDB interactive mode |
| Production SQL query deployment | ksqlDB headless mode |
| Complex custom Java logic | Kafka Streams |

---

# Part VII — Important Mental Models

---

## 60. Mental Model 1: A Stream Is an Infinite Timeline of Facts

Do not think of a stream as merely records arriving now.

Think of it as:

```text
an infinite sequence of timestamped facts
```

The timestamp is part of the meaning of the event.

---

## 61. Mental Model 2: Arrival Order Is Not Truth

Kafka records arrive in offset order per partition, but offset order is not necessarily event-time order.

Therefore:

```text
What arrived first?
```

is not the same as:

```text
What happened first?
```

This distinction is central to the lecture.

---

## 62. Mental Model 3: Windows Make Infinite Streams Finite

You cannot compute final results over an infinite stream unless you define a finite scope.

Windows provide that finite scope.

```text
infinite stream
→ finite time window
→ computable aggregation/join
```

---

## 63. Mental Model 4: Grace Period Is a Correctness Buffer

A grace period is not the window size.

The window size defines what belongs together logically.

The grace period defines how long you wait for late arrivals.

Example:

```text
Window size: 60 seconds
Grace period: 5 seconds
```

Means:

```text
Group events into 60-second buckets.
After the bucket ends, wait 5 more seconds for late events.
```

---

## 64. Mental Model 5: Suppress Means “Only Final Results, Please”

Without suppress:

```text
partial result emitted
updated result emitted
updated result emitted
final result emitted
```

With suppress:

```text
final result emitted only when the window closes
```

This is useful when intermediate results are misleading or undesirable.

---

## 65. Mental Model 6: Windowed Joins Need Memory

A stream-stream join cannot work without remembering recent records.

If event A arrives now and event B arrives later, the system must remember A long enough to join it with B.

That memory is implemented using local state stores.

---

## 66. Mental Model 7: ksqlDB Is SQL That Builds Kafka Streams Topologies

When you write a ksqlDB query, ksqlDB translates it into Kafka Streams topology logic.

So:

```text
SQL statement
→ parsed by SQL engine
→ translated into Kafka Streams topology
→ executed continuously
```

---

# Part VIII — Likely Exam / Comprehension Questions

---

## 67. Why is event time usually preferred over processing time?

Because event time reflects when the event actually occurred. Processing time reflects when the application happened to process the event, which can vary due to delays, lag, failures, or reprocessing.

Event time gives more correct results for domain-time operations such as windowed aggregations and joins.

---

## 68. What does a timestamp extractor do?

It assigns a timestamp to each Kafka record consumed by Kafka Streams.

This timestamp controls event-time behavior for windows, joins, aggregations, and time-driven processing.

---

## 69. Why are windows needed in stream processing?

Streams are unbounded, so computations like counts, averages, and joins need finite boundaries.

Windows create finite time-based groups over an infinite event stream.

---

## 70. What is the difference between tumbling and hopping windows?

Tumbling windows are fixed-size and non-overlapping.

Hopping windows are fixed-size and may overlap because the advance interval can be smaller than the window size.

---

## 71. What is a session window?

A session window is a variable-size window based on activity and inactivity.

A new session begins when the inactivity gap between events exceeds a configured threshold.

---

## 72. Why must KStream-KStream joins be windowed?

Because both streams are unbounded. Without a window, the processor would conceptually need to join against infinite history.

A window limits the time range in which records can match.

---

## 73. What is a grace period?

A grace period is the amount of time a window remains open after its end to accept late-arriving events.

It balances completeness against latency.

---

## 74. What does suppress do?

Suppress buffers intermediate aggregation updates and emits only the final result when the window closes.

It is useful when consumers should only see complete window results.

---

## 75. Why does windowing change the key type?

Because after windowing, the key must identify both the original key and the time window.

For example:

```text
String
```

becomes:

```text
Windowed<String>
```

---

## 76. Why can rekeying be expensive?

Rekeying can trigger repartitioning, which means Kafka Streams must redistribute records across partitions according to the new key.

This involves network, disk, and processing overhead.

---

## 77. What is a materialized store?

A materialized store is a local state store that maintains computed stream-processing results, such as counts, aggregates, or join state.

It enables stateful processing and interactive queries.

---

## 78. When should you use the Processor API instead of the DSL?

Use the Processor API when the DSL is not expressive enough and you need fine-grained control over metadata, forwarding, scheduling, or state stores.

---

## 79. What is ksqlDB?

ksqlDB is an event streaming database that provides a SQL interface over Kafka Streams and Kafka Connect.

It lets users define streams, tables, queries, materialized views, joins, aggregations, filters, transformations, and windows using SQL.

---

## 80. What is the difference between push and pull queries?

A push query runs continuously and emits new results as data arrives.

A pull query reads the current state from a materialized view or table.

---

# Part IX — Compact Lecture Summary

This lecture explains how Kafka Streams and ksqlDB process event streams with respect to time. The main challenge is that events may arrive out of order, so stream processors must distinguish event time, ingestion time, and processing time. Event time is usually preferred because it represents when the event actually occurred. Kafka Streams uses timestamp extractors to assign timestamps to records.

Windows make infinite streams finite by grouping events into time-bounded sets. Tumbling windows are fixed and non-overlapping, hopping windows are fixed and overlapping, session windows are based on activity gaps, sliding join windows are used for stream-stream joins, and sliding aggregation windows provide fine-grained close-in-time aggregation. Windowed aggregations maintain local materialized stores, and their results may change when late events arrive. Grace periods define how long late events are accepted. Suppress can buffer intermediate results and emit only final window results.

Windowed joins combine records from two streams when they have the same key and occur close together in time. Kafka Streams uses local state stores to remember recent records for joins and aggregations. Querying windowed stores requires both key and time range because keys include the original key plus the window.

The lecture also compares Kafka Streams DSL, Processor API, and ksqlDB. The DSL is high-level and suitable for most cases. The Processor API offers low-level control but is more complex and error-prone. ksqlDB provides SQL-based stream processing, integrates Kafka Streams and Kafka Connect, supports streams and tables, push and pull queries, materialized views, and interactive or headless deployment modes. The rule of thumb is: use ksqlDB if the problem fits SQL; use Kafka Streams if more control is needed.

---

# Part X — One-page Memory Version

```text
Stream processing over time is hard because events can arrive late or out of order.

Time semantics:
- Event time: when the event happened.
- Ingestion time: when Kafka appended it.
- Processing time: when the app processed it.

Event time is usually best for correctness.
Kafka Streams gets timestamps through TimestampExtractors.

Windows make infinite streams finite:
- Tumbling: fixed, non-overlapping.
- Hopping: fixed, overlapping.
- Session: activity-based, variable size.
- Sliding join: join records close in time.
- Sliding aggregation: continuous close-in-time aggregation.

Windowed aggregations produce KTables and often materialized stores.
Late events can update results.
Grace period = how long to accept late events.
Suppress = emit only final window result.

Windowed joins need same key + close timestamps.
KStream-KStream joins must be windowed because streams are unbounded.
Joins and aggregations need local state stores.

Windowed keys = original key + time window.
Rekeying may be needed but causes repartitioning, so filter first.

Kafka Streams DSL = high-level API for most use cases.
Processor API = low-level API when you need full control.
ksqlDB = SQL interface over Kafka Streams and Kafka Connect.
Use ksqlDB if it fits SQL; use Kafka Streams if you need custom control.
```

# Patterns: Idempotent Writer & Idempotent Reader

Two complementary patterns ensuring duplicates — inevitable with retries
in distributed systems — do not cause incorrect processing.

## Idempotent Writer

- **Retries may produce duplicate events**
- Idempotent writer ensures duplicates are **discarded**
- Guarantees **each event is written once**

```
                         Duplicate Event
                              ↓
Event Source ──► [4][3][2][2][1] ──► Idempotent Write ──► [4][3][2][1]
```

Kafka configuration:
```
enable.idempotence=true
```

## Idempotent Reader

- **Reader may see duplicate events** in the input stream
- Idempotent reader handles duplicates **safely**
- Ensures **correct processing**

```
                                      Event Processor
Input Stream [4][3][2][2][1] ──► [4][3][2][1] ──► ░░░░░░
                  ↑              Idempotent Read
           Duplicate Event
```

Kafka configuration:
```
isolation.level="read_committed"
```

## Exactly-Once End-to-End

For full exactly-once in Kafka Streams (applies to both sides):
`processing.guarantee="exactly_once"`

- Writer deduplicates producer retries at the broker
- Reader only reads committed transactions
- Together: exactly-once semantics within the Kafka ecosystem

## Caveat

Exactly-once applies to **Kafka-internal** state transitions. External
side effects (HTTP calls, emails, payments) still require idempotency at
the external system level.

## Source

Lecture 8, HSG ICS. See
https://www.confluent.io/blog/simplified-robust-exactly-one-semantics-in-kafka-2-5/

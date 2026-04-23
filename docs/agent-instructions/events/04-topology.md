# Topology

A stream processing application is organized as a **topology** — a directed
processing graph through which events flow.

## Elements

- **Source processors** — read from source streams (e.g. Kafka topics)
- **Stream processors** — intermediate nodes performing operations
- **Sink processors** — write to sink streams
- **Streams** — edges connecting processors

Events flow through the processors, forming a processing graph. In production,
the pipeline is **distributed over many nodes**.

## Example Topology — CryptoSentiment

A stateless processing pipeline analyzing tweets for crypto sentiment:

```
tweets
  ↓
Filter                                  (1) keep only crypto tweets
  ↓
Branch                                  (2) split by language
  ├── English (#Bitcoin, #Ethereum)
  └── Non-English (Bittokoin, 이더리움)
         ↓
       Translate (map)                  (3) translate non-English
         ↓
Merge ← both branches                   (4) re-combine into single stream
  ↓
Add sentiment (flatMap)                 (5) emit sentiment-annotated events
  ↓
crypto-sentiment                        (sink topic)
```

Observations:
- A **branch** produces multiple output substreams
- **Merge** recombines substreams into a single downstream stream
- Each node performs one specific operation; composition forms the pipeline

## Source

Lecture 7, HSG ICS. Example from Mitch Seymour, *Mastering Kafka Streams and ksqlDB*.
Implementation: https://github.com/mitch-seymour/mastering-kafka-streams-and-ksqldb

# Changelog

Track what was changed, why it was changed, and any important notes.

## Entry Format

```markdown
### [YYYY-MM-DD] - [Contributor Name]

#### What
- List changes here

#### Why
- Explain reasoning

#### Remarks
- Optional notes, issues, or future work
```

---

### [2026-03-01] - Cyril Gabriele

#### What
- Copied Amin's spring-kafka code snippets into our repo and hooked up the wiring so the service boots correctly.

#### Why
- We want to adjust those s.t. they then fulfill our MVP of consuming the Binance stream and emitt boughtCurrency event.

#### Remarks
- none

### [2026-03-01] - Cyril Gabriele

#### What
- Implemented the Binance rolling window ingest service (WebSocket client + Kafka producer) and supporting models under `src/main/java/ch/unisg/kafka/spring/model` and `service`.
- Added a dedicated Market Rolling Kafka template plus application properties for topic/stream configuration and runtime logging of all published deltas.
- Documented how to run the ingest flow (Docker Kafka + `mvn spring-boot:run`) in the README.

#### Why
- Needed an automated service that listens to `!ticker_1h@arr`, fans out symbol deltas, and publishes them to `market.rolling.1h` with the symbol key to satisfy the MVP ingestion requirement.

#### Remarks
- Future improvement: consider adding backoff jitter and DLQ publishing for malformed payloads.

### [2026-03-01] - Cyril Gabriele

#### What
- Removed the legacy SuperHero model, controllers, producer injections, and Kafka consumer wiring so the project focuses entirely on the Binance rolling window events.
- Simplified producer/consumer beans to only support string messages and the `MarketRollingWindowEvent` payload, updating `application.yml` with the remaining properties.

#### Why
- The SuperHero examples were placeholders; keeping them created confusion and unused wiring once the real ingest path existed.

#### Remarks
- none

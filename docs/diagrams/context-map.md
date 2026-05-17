# Context Map

## Bounded Context View

```mermaid
graph TB
    User["End user"]
    Binance["Binance"]
    Camunda["Camunda"]
    FxProvider["FX provider<br/>(exchangerate.host)"]

    subgraph CryptoFlow
        MDC["Market Data Context"]
        RDC["Reference Data Context"]
        UIC["User Identity Context"]
        OBC["Onboarding Context"]
        TRC["Trading Context"]
        PFC["Portfolio Context"]
    end

    User --> OBC
    User --> TRC
    Binance --> MDC
    FxProvider --> RDC

    OBC -.->|"orchestrated in"| Camunda
    TRC -.->|"orchestrated in"| Camunda

    RDC -->|"FX rates"| MDC
    RDC -->|"FX rates"| TRC
    RDC -->|"FX rates"| PFC
    MDC -->|"localized prices"| TRC
    MDC -->|"localized prices"| PFC
    UIC -->|"confirmed users"| TRC
    UIC -->|"display currency"| TRC
    UIC -->|"display currency"| PFC
    TRC -->|"approved orders"| PFC
    UIC -->|"compensation events"| PFC
    PFC -->|"compensation events"| UIC
```

Notes:

- `Market Data Context` owns market-price ingestion and publication, and (per ADR-0030) the stream-side enrichment that joins prices against FX rates into `crypto.price.localized`.
- `Reference Data Context` owns slow-moving externally-sourced facts such as FX rates (see ADR-0029); it is currently realised by `fx-rate-service`.
- `User Identity Context` owns users, confirmation state, confirmed-user events, and the per-user **Display Currency** (see ADR-0028).
- `Onboarding Context` coordinates the registration flow across user and portfolio creation.
- `Trading Context` owns pending orders, matching, and order approval; it converts buy-time quotes to the user's Display Currency at API read time.
- `Portfolio Context` owns holdings, valuation, and the local price read model; it converts portfolio totals to the user's Display Currency at API read time.

## Overview

```mermaid
graph TB
    User["End user"]
    Binance["Binance feeds<br/>(external market source)"]
    FxProvider["FX provider<br/>(external)"]
    Camunda["Camunda orchestration"]

    subgraph Platform
        MD["market-data-service"]
        FX["fx-rate-service"]
        US["user-service"]
        ONB["onboarding-service<br/>(deploys onboarding BPMN)"]
        TR["transaction-service"]
        PF["portfolio-service"]
        SE["shared-events<br/>(Kafka contracts)"]
    end

    User --> Camunda
    Binance --> MD
    FxProvider --> FX

    FX -->|"reference.fx.rate (compacted)"| MD
    FX -->|"reference.fx.rate (compacted)"| PF
    FX -->|"reference.fx.rate (compacted)"| TR

    MD -->|"crypto.price.raw"| PF
    MD -->|"crypto.price.raw"| TR
    MD -->|"crypto.price.localized"| PF
    MD -->|"crypto.price.localized"| TR
    MD -->|"crypto.ohlc.{interval}"| PF
    MD -->|"crypto.ohlc.{interval}"| TR

    ONB -.->|"deploys userOnboarding"| Camunda
    TR -.->|"deploys placeOrder"| Camunda
    Camunda -.->|"onboarding jobs"| US
    Camunda -.->|"onboarding jobs"| PF
    Camunda -.->|"order jobs"| TR

    US -->|"user.confirmed (compacted)"| TR
    US -->|"user.display-currency (compacted)"| PF
    US -->|"user.display-currency (compacted)"| TR
    US -->|"crypto.portfolio.compensation"| PF
    PF -->|"crypto.user.compensation"| US

    TR -->|"transaction.order.approved"| PF

    SE -. shared event contracts .- MD
    SE -. shared event contracts .- FX
    SE -. shared event contracts .- US
    SE -. shared event contracts .- TR
    SE -. shared event contracts .- PF
```

## Notes

- `market-data-service` ingests Binance market feeds, publishes raw price data, and hosts the scope-03 streams module that emits `crypto.price.localized` (Avro per ADR-0032). It also hosts the scope-05 OHLC streams module that emits `crypto.ohlc.{1m,5m,1h}` (USDT-denominated, per ADR-0031).
- `fx-rate-service` polls a public FX provider on a 5-minute timer and publishes `reference.fx.rate` (compacted, Avro). It is the only producer in the Reference Data context (ADR-0029).
- `user-service` publishes `user.confirmed`; `transaction-service` keeps a local confirmed-user read model from that compacted topic.
- `user-service` additionally publishes `user.display-currency` (compacted, Avro) on user creation and on every `PATCH /users/{id}/display-currency`. Both `portfolio-service` and `transaction-service` materialise it as a KTable to convert values at API read time (ADR-0028).
- `portfolio-service` consumes price updates (`crypto.price.localized`), approved-order events, and `user.display-currency` to render per-user portfolio values.
- `transaction-service` consumes `crypto.price.localized` and `user.display-currency` to render buy-time quotes in the user's Display Currency. Order placement itself remains USDT-internal.
- `onboarding-service` deploys the onboarding BPMN, while `transaction-service` deploys and runs the order workflow.
- `Camunda` coordinates the `userOnboarding` and `placeOrder` flows across the participating services.
- Compensation between `user-service` and `portfolio-service` is handled asynchronously through Kafka topics.
- Schema Registry (`http://schema-registry:8081` inside the Docker network, `http://localhost:8090` from the host) is part of the Kafka platform, not shown in the context diagrams; it is the runtime contract surface for all Avro topics introduced by ADRs 0028 through 0032.

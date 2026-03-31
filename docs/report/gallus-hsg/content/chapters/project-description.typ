= Project Description <project-description>

CryptoFlow is a crypto portfolio simulation platform that demonstrates event-driven and process-oriented architecture patterns in a distributed microservice environment. The system allows users to register, manage cryptocurrency portfolios, and place simulated trading orders — all coordinated through asynchronous event streams and orchestrated business processes.

== Domain and Goals

The project targets the cryptocurrency trading domain, simulating a platform where users can track real-time prices and manage virtual portfolios. The primary goal is not to build a production trading system, but to serve as a vehicle for applying the architectural concepts covered in the EDPO course: event-driven communication, process orchestration, saga patterns, and compensation mechanisms.

The system addresses several concrete challenges:
- Ingesting high-frequency market data from an external source and distributing it to downstream consumers without tight coupling.
- Maintaining portfolio valuations that reflect current market prices without synchronous inter-service calls.
- Orchestrating multi-step business processes (user onboarding, order placement) across service boundaries with well-defined failure handling.
- Implementing compensating transactions when parts of a distributed workflow fail.

== System Overview

CryptoFlow consists of five Spring Boot microservices, an Apache Kafka cluster, a PostgreSQL database, and Camunda 8 (Zeebe) running as a managed SaaS platform:

- *Market Data Service* (port 8081) subscribes to Binance WebSocket ticker streams for six cryptocurrency pairs (BTC, ETH, SOL, BNB, XRP, LTC against USDT) and publishes `CryptoPriceUpdatedEvent` records to the `crypto.price.raw` Kafka topic.

- *Portfolio Service* (port 8082) consumes price events into a local in-memory cache (`ConcurrentHashMap`) and exposes REST endpoints for querying prices and calculating portfolio valuations. It also participates in the onboarding saga as a Camunda job worker that creates portfolio records.

- *Transaction Service* (port 8083) handles order placement through a Camunda BPMN process. It consumes live price events, matches pending orders against market prices, and correlates execution results back to the orchestrating process.

- *User Service* (port 8084) manages user accounts and participates in the onboarding saga. It generates email confirmation links, creates user records upon confirmation, and handles compensation (user deletion) when the saga fails.

- *Onboarding Service* (port 8085) acts as the saga orchestrator. It deploys the `userOnboarding.bpmn` process to Zeebe and coordinates parallel user and portfolio creation, email confirmation, timeout handling, and compensation across the user and portfolio services.

== Technology Stack

The technology choices reflect the course curriculum and the requirements of a distributed event-driven system:

- *Java 21* and *Spring Boot 3.5* provide the service runtime, dependency injection, and web layer.
- *Apache Kafka* (Confluent 7.6, KRaft mode) serves as the sole inter-service communication channel for domain events, with topics explicitly created and managed via Spring `@Bean` definitions.
- *Camunda 8 / Zeebe* (SaaS) orchestrates multi-step business processes through BPMN models, with services acting as stateless job workers connected via gRPC.
- *PostgreSQL 16* with *Flyway* migrations stores persistent data for the portfolio and user bounded contexts. Hibernate runs in `validate` mode only.
- *Docker Compose* provisions the full local infrastructure stack (Kafka, PostgreSQL, Kafka UI, pgAdmin) alongside all five application services.
- The *Binance WebSocket API* provides real-time cryptocurrency price feeds at no cost and without authentication.

All services share event definitions through a `shared-events` Maven module, ensuring compile-time consistency of event schemas across the entire system.

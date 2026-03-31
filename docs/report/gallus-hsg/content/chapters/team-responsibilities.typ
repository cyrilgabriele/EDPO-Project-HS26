= Team Responsibilities <team-responsibilities>

The project was developed by a two-person team over the course of the FS2026 semester. Both team members contributed to architectural design and system integration, while individual service ownership was divided by bounded context.

#figure(
  caption: "Overview of team responsibilities and contributions",
  table(
    columns: (auto, 1fr, 2fr),
    [*Team Member*], [*Area of Ownership*], [*Description*],
    "Ioannis Theodosiadis",
    [
      - Architecture design
      - Portfolio Service
      - Transaction Service
      - Kafka experiments
      - Documentation
    ],
    [Designed the overall system architecture and hexagonal service structure. Implemented the portfolio service (Kafka consumer, local price cache, REST API, JPA persistence with Flyway), the user service (Camunda workers, confirmation flow, compensation logic), and the transaction service (order matching, BPMN process). Led architecture decision documentation and report writing.],
    "Cyril Gabriele",
    [
      - Architecture design
      - Market Data Service
      - Onboarding Service
      - Kafka experiments
      - Infrastructure
    ],
    [Co-designed the system architecture. Implemented the market data service (Binance WebSocket integration, Kafka producer with deterministic partitioning). Built the onboarding service as the saga orchestrator with the `userOnboarding.bpmn` process. Designed and ran the Kafka reliability experiments. Set up Docker Compose infrastructure and CI configuration.],
  ),
) <tab:team-responsibilities>

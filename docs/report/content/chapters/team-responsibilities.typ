= Team Responsibilities <team-responsibilities>

The project was developed by a two-person team over the course of the FS2026 semester. Both team members contributed to architectural design and system integration, while individual service ownership was divided by bounded context. We kept the contribution balanced between both members.

@tab:team-responsibilities summarizes the concrete ownership split and the major implementation responsibilities of each team member.

#figure(
  caption: "Overview of team responsibilities and contributions",
  table(
    columns: (auto, 1fr, 2fr),
    [*Team Member*], [*Area of Ownership*], [*Description*],
    "Ioannis",
    [
      - Architecture design
      - Place order process
      - Portfolio Service
      - Transaction Service
      - Reference data services
      - OHLC and portfolio valuation streams
      - Infrastructure
      - Kafka experiments
      - Schema Registry and Avro contracts
      - Documentation
    ],
    [Designed the overall system architecture and service structure. Owned and implemented the place order flow, transaction-service orchestration, portfolio-service persistence and Kafka consumption, and the second-half portfolio valuation topology with interactive queries. Implemented the Reference Data additions around FX rates, coin metadata, display-currency integration, Schema Registry/Avro contracts, and OHLC enrichment. Led infrastructure setup for local integration, contributed to Kafka reliability experiments, authored architecture decisions, and report documentation.],
    "Cyril",
    [
      - Architecture design
      - Onboarding process
      - Market Data Service
      - Partial book ingestion
      - Market Order Scout
      - Bid/ask matching extension
      - User Service
      - Onboarding Service
      - Kafka experiments
      - Documentation
    ],
    [Co-designed the system architecture. Owned and implemented the onboarding process end to end across the onboarding service and user service, including the `userOnboarding.bpmn` orchestration, Camunda workers, confirmation handling, compensation behavior, and display-currency preference publication. Built the market data service and the market-scout extension: Binance ticker ingestion, partial-book ingestion, ask-side filtering and Avro-derived scout events, dashboard state, and the bid/ask matching integration with transaction-service. Contributed to Kafka reliability experiments, authored architecture decisions, and report documentation.],
  ),
) <tab:team-responsibilities>

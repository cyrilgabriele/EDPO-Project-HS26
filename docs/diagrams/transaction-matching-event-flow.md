# Transaction Matching Event Flow

This single diagram shows how market data and user orders become a transaction execution decision. It focuses on the event path relevant to matching and intentionally omits scout-only diagnostic topics, retries, dashboards, and persistence details outside the matching decision.

```mermaid
flowchart TD
    Binance["Binance partial book depth stream"]
    Ingestion["market-partial-book-ingestion-service"]
    Raw["crypto.scout.raw<br/>RawOrderBookDepthEvent"]

    subgraph Scout["market-order-scout-service"]
        AskFilter["Keep ask-side levels"]
        AskTranslator["Flatten order book asks<br/>into ask quotes"]
        MatchableTranslator["Translate ask quotes<br/>to MatchableAsk"]
    end

    MatchableAsks["crypto.scout.matchable-asks<br/>MatchableAsk<br/>key: symbol"]

    User["End user"]
    Camunda["Camunda placeOrder process"]
    PlaceOrder["transaction-service<br/>placeOrderWorker"]
    BuyBids["transaction.buy-bids<br/>BuyBid<br/>key: symbol"]

    subgraph Matcher["transaction-service Kafka Streams matcher"]
        BidStream["Consume BuyBid stream"]
        AskStream["Consume MatchableAsk stream"]
        PendingBids["Store pending bids<br/>by symbol"]
        EventTimeMatch["Match on event time window<br/>ask.eventTime within bid.createdAt + 30s"]
        PriceQuantityCheck["Check executable conditions<br/>bid price >= ask price<br/>bid quantity <= ask quantity"]
        Allocation["Allocate each ask to at most one bid<br/>price-time priority"]
    end

    OrderMatched["transaction.order-matched<br/>OrderMatched"]
    MatchConsumer["transaction-service<br/>OrderMatched consumer"]
    PriceMatched["Camunda message<br/>priceMatchedEvent<br/>correlated by transactionId"]
    ApproveOrder["transaction-service<br/>approve and publish approved order"]
    OrderApproved["transaction.order.approved<br/>OrderApprovedEvent"]
    Portfolio["portfolio-service<br/>updates holdings"]

    Binance --> Ingestion
    Ingestion --> Raw
    Raw --> AskFilter
    AskFilter --> AskTranslator
    AskTranslator --> MatchableTranslator
    MatchableTranslator --> MatchableAsks

    User --> Camunda
    Camunda --> PlaceOrder
    PlaceOrder --> BuyBids

    BuyBids --> BidStream
    MatchableAsks --> AskStream

    BidStream --> PendingBids
    AskStream --> EventTimeMatch
    PendingBids --> EventTimeMatch
    EventTimeMatch --> PriceQuantityCheck
    PriceQuantityCheck --> Allocation
    Allocation --> OrderMatched

    OrderMatched --> MatchConsumer
    MatchConsumer --> PriceMatched
    PriceMatched --> Camunda
    Camunda --> ApproveOrder
    ApproveOrder --> OrderApproved
    OrderApproved --> Portfolio

    style Raw fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style MatchableAsks fill:#e3f2fd,stroke:#1565c0,stroke-width:2px
    style BuyBids fill:#fff8e1,stroke:#ef6c00,stroke-width:2px
    style OrderMatched fill:#f3e5f5,stroke:#6a1b9a,stroke-width:2px
    style OrderApproved fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
```

Read from top to bottom: Scout turns Binance order-book asks into executable `MatchableAsk` events, while the order workflow turns user orders into `BuyBid` events. The transaction matcher consumes both streams by symbol, keeps pending bids, evaluates arriving asks against the 30-second event-time window and price/quantity rules, then emits `OrderMatched` when execution is possible.

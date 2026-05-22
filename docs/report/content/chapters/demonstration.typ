= Live System Demonstration <demonstration>

CryptoFlow ships purpose-built dashboards for the user-facing and process-facing services. Every page polls its own backend on a one- to five-second interval and renders the current system state without any manual refresh. The figure slots below document the demo surfaces; the visible red notes mark the screenshot replacements delegated before hand-in.

== market-data-service — Event Notification

#figure(
  image("figures/market-data-demo.png", width: 100%),
  caption: [market-data-service dashboard showing the live Kafka event stream],
) <fig:demo-market-data>

#text(red)[TODO: Update this screenshot to show the current dashboard with the OHLC candlestick chart, coin metadata panel, interval selector, chart statistics, and live event stream.]

The market-data-service dashboard (@fig:demo-market-data) combines the raw event notification stream with the implemented OHLC topology. The upper panel renders a candlestick chart from closed OHLC bars that the co-located Kafka Streams application writes to `crypto.ohlc.1m`, `crypto.ohlc.5m`, and `crypto.ohlc.1h`. The symbol selector, interval selector, bar count, last close, and percentage-change statistics are read from the dashboard's local OHLC snapshot. When the metadata join has produced enriched bars, the coin panel shows the asset name, base/quote pair, logo, and market-cap rank from `reference.crypto.metadata`.

The lower panel still exposes the raw `crypto.price.raw` feed. Each row corresponds to one Kafka record and shows the symbol, USDT price, partition, offset, original exchange timestamp, producer publication time, and event UUID. This makes the Event Notification pattern visible while also showing how the same source stream is reused by downstream topologies without changing the producer.

== portfolio-service — Event-Carried State Transfer

#figure(
  image("figures/portfolio-demo.png", width: 100%),
  caption: [portfolio-service dashboard showing the local price cache and all portfolio holdings],
) <fig:demo-portfolio>

#text(red)[TODO: Update this screenshot to show the current dashboard with USDT price cache, FX cache, portfolio display-currency selector, converted totals, and copied user IDs.]

The portfolio-service dashboard (@fig:demo-portfolio) shows three local read-side replicas. The USDT price cache is filled from `crypto.price.raw`, the FX cache is filled from `reference.fx.rate`, and the display-currency cache is filled from `user.display-currency`. Because these caches are populated by consuming events rather than by querying other services at request time, no dashboard request calls market-data-service, fx-rate-service, or user-service synchronously. This is the defining property of Event-Carried State Transfer: the consumer owns the local state it needs.

The portfolio cards show the user's name, UUID, copied user-id shortcut, display-currency selector, converted total, USDT subtotal when applicable, current FX rate, and a holdings table with per-symbol quantity, display-currency price, converted value, and creation timestamp. The running implementation performs conversion at API read time from the USDT price cache and FX cache. The ADR-0030 `crypto.price.localized` stream remains a target design, not the path used by this dashboard. The small propagation delay between an approved order in transaction-service and a new holding in portfolio-service illustrates the latency introduced by the asynchronous event flow.

== transaction-service — Bid/Ask Matching, Outbox, and Replicated Read-Model

#figure(
  image("figures/transaction-demo.png", width: 100%),
  caption: [transaction-service dashboard showing orders, outbox events, and the local confirmed-user projection],
) <fig:demo-transaction>

#text(red)[TODO: Update this screenshot to show the current dashboard with buy-time quote widget, orders, outbox events, confirmed-user projection, and matching audit.]

The transaction-service dashboard (@fig:demo-transaction) starts with a buy-time quote widget. It reads the user's display currency from the local `user.display-currency` cache, the latest USDT price from the local `crypto.price.raw` cache, and the current FX rate from `reference.fx.rate`. The widget previews the display-currency price that the user sees before placing an order through Camunda; it is a read-side helper, while actual order placement still goes through `placeOrder.bpmn`.

The Orders table lists all placed orders with their status (`PENDING`, `APPROVED`, or `REJECTED`), the target price specified by the user, the matched ask price that triggered the transition, and the timestamps for placement and resolution. Orders remain `PENDING` until the transaction matching topology allocates a `MatchableAsk` to the order's `BuyBid` and emits `transaction.order-matched`. Only then does the Zeebe workflow advance and the `approveOrderWorker` write the `APPROVED` status.

The Outbox Events table confirms the Transactional Outbox pattern: each approved order has a corresponding row in the `outbox_event` table that was written atomically in the same database transaction as the status update. A scheduler running every 500 ms reads unpublished rows, publishes them to Kafka, and marks them as published. The `Published At` timestamp and the checkmark in the State column prove that the event has left the service's local store. So long as the database transaction commits, the event will eventually be published. This eliminates the dual-write problem.

The Confirmed Users section shows the service's local `confirmed_user` projection, which is populated by consuming `UserConfirmedEvent` messages from Kafka. It also displays the cached display currency so a confirmed user can be selected directly for the quote widget. Order validation reads this projection to check whether a requesting user has completed onboarding, without making a synchronous call to the onboarding-service or user-service. This is the Replicated Read-Model pattern: each service keeps a local, eventually-consistent copy of the data it depends on, trading some storage for autonomy and resilience.

The Matching Audit section ties the stream-processing extension back to the workflow. It joins the recently observed `BuyBid`, `MatchableAsk`, and `transaction.order-matched` audit rows in the dashboard response so each match can be inspected with bid price and quantity, ask quote id, matched price and quantity, event-time timeline, source venue, and Kafka topic/partition/offset metadata.

== market-order-scout-service — Windowed Ask-Price Distribution

#figure(
  block(width: 100%, height: 7cm, stroke: 1pt + gray, inset: 1em)[
    #align(center + horizon)[
      #text(gray)[TODO: Add screenshot of the market-order-scout-service dashboard.]
    ]
  ],
  caption: [market-order-scout-service dashboard showing windowed minimum ask-price distributions],
) <fig:demo-market-order-scout>

The market-order-scout-service dashboard (@fig:demo-market-order-scout) consumes the materialized dashboard state exposed by its Kafka Streams topology. It shows one panel per symbol with a histogram of `log(minAskPrice)` across the retained window summaries, the number of retained windows, and the statistical moments mean, variance, skewness, and kurtosis. A separate topics section lists the raw input topic, derived ask-quote stream, matchable-ask stream, ask-opportunity stream, and window-summary topic used by the service.

== onboarding-service — Parallel Saga via Camunda

#figure(
  image("figures/onboarding-demo.png", width: 100%),
  caption: [onboarding-service dashboard showing live Camunda process instances enriched with user variables],
) <fig:demo-onboarding>

#text(red)[TODO: Update this screenshot to show the current dashboard with process instance statistics, the instance table, error banner state, and expandable process-flow section.]

The onboarding-service dashboard (@fig:demo-onboarding) queries the Camunda Operate REST API and displays the twenty most recent instances of the `UserOnboarding` BPMN process. The page summarizes process-instance statistics, lists instances in a table, and exposes an expandable process-flow section for the onboarding BPMN. Each row shows the instance state, the user name, email address, generated user UUID resolved from Zeebe process variables, the full instance key, and the start and end timestamps. Active instances have a pulsing green state badge, while completed instances are rendered in a muted style.

The dashboard is filtered to the latest deployed version of the process definition, so re-deployments during development do not mix old and new instances in the same view. Enriching the instance list with user-level variables requires Operate variable queries for each displayed instance; the results are joined in the onboarding-service response before the browser renders them. An error banner appears if the Operate API is unreachable, making connectivity issues immediately visible without breaking the rest of the page.

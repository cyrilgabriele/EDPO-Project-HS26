# 28. Use a dedicated compacted topic for per-user Display Currency

Date: 2026-05-16

## Status

Accepted

## Context

The platform values portfolios and renders buy-time quotes in USDT today. Users want to see those values in a currency of their choice (CHF, EUR, GBP, USD). The currency choice is User-Identity data: user-service is the source of truth, but portfolio-service and transaction-service both need it to convert values at API read time.

Three propagation shapes were considered: extending `user.confirmed` with a `displayCurrency` field, synchronous HTTP lookups against user-service, or a new compacted topic carrying the preference. The first conflates a one-time identity-confirmation event with a mutable preference; the second violates the Event-Carried State Transfer pattern adopted in ADR-0002. A separate scope-of-currency question also had to be answered: does the choice govern presentation only, or does it re-denominate stored holdings and reporting?

## Decision

Introduce **Display Currency** as a per-user, ISO-4217 currency code owned by user-service. Display Currency governs **presentation only**. Holdings, orders, and any portfolio valuation snapshots remain USDT-denominated, and conversion happens at API read time.

- Add a `display_currency` column to the user table; default `'USD'` on user creation; never null.
- Add a new endpoint `PATCH /users/{id}/display-currency` to change it.
- Publish a `UserDisplayCurrencyUpdated` event (Avro, per ADR-0032) to a new compacted Kafka topic `user.display-currency`, keyed by userId, on user creation and on every successful PATCH.
- portfolio-service and transaction-service materialize the topic as a KTable / GlobalKTable; they never call user-service synchronously to fetch the preference.
- The supported initial set is `{USD, EUR, CHF, GBP}`; PATCH validates against this set.

The canonical term in code, schemas, and docs is **Display Currency**. "Preferred Currency", "Reporting Currency", and "Local Currency" are avoided as ambiguous.

## Consequences

Mirrors the `user.confirmed` and `reference.fx.rate` patterns already in use, keeping inter-service communication uniformly Kafka per ADR-0001 and uniformly ECST per ADR-0002. Settings changes do not pollute identity-lifecycle events. Because the column is never null and the topic is compacted from first creation, consumers can rely on every userId having a Display Currency in their materialized view.

Display-only scope keeps the blast radius small: no historical re-denomination, no per-currency accounting, and order/holding storage is unchanged. If the platform later needs accounting-style reporting in a non-USDT currency, this ADR will need to be revisited and a separate "unit of account" concept introduced.

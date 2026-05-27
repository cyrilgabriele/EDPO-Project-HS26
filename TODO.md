# Report Hand-In TODOs

Ioannis, please complete the remaining hand-in cleanup for the report.

## Screenshot and Figure Replacements

- Replace the context map in `docs/report/content/chapters/project-description.typ`.
  - Current marker: `TODO Ioannis: Replace Figure 1 with the most recent context/topology image before hand-in.`
  - Keep the surrounding text and caption aligned with the final figure.

- Replace the `market-data-service` dashboard screenshot in `docs/report/content/chapters/demonstration.typ`.
  - It should show the current dashboard with OHLC candlestick chart, coin metadata panel, interval selector, chart statistics, and live event stream.

- Replace the `portfolio-service` dashboard screenshot in `docs/report/content/chapters/demonstration.typ`.
  - It should show the current dashboard with USDT price cache, FX cache, portfolio display-currency selector, converted totals, and copied user IDs.

- Replace the `transaction-service` dashboard screenshot in `docs/report/content/chapters/demonstration.typ`.
  - It should show the current dashboard with buy-time quote widget, orders, outbox events, confirmed-user projection, and matching audit.

- Add the missing `market-order-scout-service` dashboard screenshot in `docs/report/content/chapters/demonstration.typ`.
  - Replace the gray placeholder block.
  - It should show the windowed minimum ask-price distribution dashboard.

- Replace the `onboarding-service` dashboard screenshot in `docs/report/content/chapters/demonstration.typ`.
  - It should show process instance statistics, the instance table, error banner state, and expandable process-flow section.

## Release Link

- Create the final GitHub release for the current report version.
- Add the new release link to:
  - `docs/report/content/chapters/project-description.typ`
  - `docs/report/content/abstract.typ`
- Keep the old release link as well. Do not replace it; add the new link alongside it so the report preserves the previous release reference and also points to the final hand-in release.

## Final Checks

- Remove the red TODO notes and the gray placeholder after replacing the corresponding assets.
- Rebuild the report:

```bash
typst compile docs/report/report.typ docs/report/report.pdf
```

- Confirm there are no remaining visible TODOs in `docs/report` unless intentionally kept:

```bash
rg -n "TODO|FIXME|PLACEHOLDER|#text\\(red\\)" docs/report
```

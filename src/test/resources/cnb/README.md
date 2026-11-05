# CNB fixtures

Real responses of `GET https://api.cnb.cz/cnbapi/exrates/daily?date=YYYY-MM-DD&lang=EN`, recorded on 2026-11-05:

- `daily-2026-11-03.json`: `date=2026-11-03` (a Tuesday), 30 currencies.
- `daily-2026-10-30.json`: `date=2026-11-01` (a Sunday). CNB answers with the previous fixing, `validFor: 2026-10-30`.

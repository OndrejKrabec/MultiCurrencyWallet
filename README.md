# Multicurrency Wallet

A small REST API that acts as a multicurrency bank account. Money can be deposited and withdrawn in
any currency the Czech National Bank (CNB) quotes, and the total balance can be shown in any of them,
converted with the current official CNB exchange rates.

Java 25 · Spring Boot 4.1 · springdoc-openapi · in-memory storage

Repository: <https://github.com/OndrejKrabec/MultiCurrencyWallet>. The design blueprint is in
[`docs/design.md`](docs/design.md); how AI was used is in [`AI_USAGE.md`](AI_USAGE.md).

## Build and run

With Java 25:

```bash
./mvnw spring-boot:run
```

Or with Docker, without a local Java:

```bash
docker build -t multicurrency-wallet . && docker run --rm -p 8080:8080 multicurrency-wallet
```

The app listens on <http://localhost:8080>. The API reference (Swagger UI) is at
<http://localhost:8080/swagger-ui.html>. The OpenAPI description is served at `/v3/api-docs` and
committed as [`docs/openapi.yaml`](docs/openapi.yaml).

To build and run all checks and tests (no CNB access needed):

```bash
./mvnw verify
```

GitHub Actions runs `./mvnw verify` and the Docker build on every push.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/wallet/deposits` | Deposit money. Returns all balances. |
| `POST` | `/wallet/withdrawals` | Withdraw money, at most the balance of that currency. Returns all balances. |
| `GET` | `/wallet` | All balances, one per currency. |
| `GET` | `/wallet/total?currency=EUR` | The total of all balances in one currency, and the date of the fixing used. |
| `GET` | `/exchange-rates` | The CNB rates in use, which are also the supported currencies (plus CZK). |
| `GET` | `/actuator/health` | Health, including the state of the CNB rates. |

- Money in JSON is always `{currency, amount}`.
- Amounts are decimal **strings**, so a client's JSON number parser can't lose precision. Requests may
  also send numbers.
- Currencies are ISO 4217 codes in upper case (`USD`).

The assignment's example workflow:

```bash
curl -s localhost:8080/wallet/deposits    -H 'Content-Type: application/json' -d '{"currency":"USD","amount":"10"}'
curl -s localhost:8080/wallet/deposits    -H 'Content-Type: application/json' -d '{"currency":"EUR","amount":"5"}'
curl -s localhost:8080/wallet/withdrawals -H 'Content-Type: application/json' -d '{"currency":"USD","amount":"3"}'
curl -s 'localhost:8080/wallet/total?currency=USD'
curl -s 'localhost:8080/wallet/total?currency=EUR'
```

With the CNB fixing of 2026-11-03 (1 USD = 21.762 CZK, 1 EUR = 24.34 CZK), the totals are:

```json
{ "currency": "USD", "amount": "12.59", "ratesValidFor": "2026-11-03" }
{ "currency": "EUR", "amount": "11.25", "ratesValidFor": "2026-11-03" }
```

How these come out: 7 + 5 × 24.34 / 21.762 = 12.5923… USD, and 5 + 7 × 21.762 / 24.34 = 11.2585… EUR.
Both are rounded down (see decision 4).

The assignment's illustrative rates give exactly 12.80 USD and 11.02 EUR. Both are reproduced in
`WalletTotalTest`, and the real fixing is reproduced in `WalletEndToEndTest`.

## Design decisions

### 1. Missing rates (weekends, holidays)

**Which rate:** the latest CNB fixing. This is CNB's own rule: a fixing is valid until the next one is
published. On weekends, holidays and before the ~14:30 publication, the latest fixing is an earlier
day's.

**How the client is told:** every total, and `GET /exchange-rates`, includes `ratesValidFor`, the
date of the fixing used. An older date on a Sunday is correct, not an error, so the API has no "stale" flag.

**"Today"** is the Prague date from an injected `Clock`, not the server's time zone.

**No holiday calendar, on purpose.** The refresh (decision 3) tells working days from weekends, but not
public holidays. On a holiday CNB keeps returning the previous fixing, which costs one cheap call every
10 minutes that afternoon (up to 13 afternoons a year, fewer when a holiday falls on a weekend). A holiday library (e.g. Jollyday, which
supports CZ) would remove those calls, but it isn't worth a dependency.

### 2. Rate units

CNB quotes some currencies per 100 or 1000 units: HUF, INR, ISK, JPY, KRW, PHP, THB and TRY per 100,
and IDR per 1000.

The unit count is kept next to the rate (`FxRate(currency, units, rate)`). A conversion is one
expression with a single division at the end:

`amount × rateFrom × unitsTo / (rateTo × unitsFrom)`

No "rate per 1 unit" is ever stored or rounded. `GET /exchange-rates` shows the rates as CNB quotes them
(`{"currency": "JPY", "units": 100, "rate": "13.755"}`).

### 3. CNB unavailable, caching

**Rates are cached in memory and refreshed in the background.** Requests only read the cache and never
wait for CNB. CNB publishes one fixing per working day and
[asks clients not to access it excessively](https://www.cnb.cz/en/financial-markets/foreign-exchange-market/central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/).

**The refresh** is a `@Scheduled` check every 10 minutes (`wallet.cnb.refresh-interval`). It calls CNB
only when a newer fixing can exist:

- at startup,
- after a failed refresh,
- on weekdays after 14:30 Prague time, until today's fixing has arrived.

That makes one to three calls per working day, with none at night, in the morning or on weekends. A
cron job only in the 14:30 window was rejected: it would not retry when CNB is down in that window, and
it would leave no rates after a restart outside it.

**When CNB can't be reached:**

- A call that fails on the network or with a 5xx is retried within the same refresh, four times after
  2, 4, 8 and 16 s (Spring's `@Retryable`). A short outage, e.g. while the app starts, then costs seconds, not 10 minutes.
- A refresh that still fails keeps the last good fixing, and clients see its date in `ratesValidFor`.
- A failed refresh is an operational concern, not an API one. It shows up as a warning in the log and
  in `/actuator/health`: the `cnb` component is `UNKNOWN`, with the last fixing's date and
  `hasLastRefreshFailed: true`, or with `rates: not loaded yet` if nothing has been loaded.
- If no rates have been loaded since startup, deposits, totals and `GET /exchange-rates` return
  **503**.
- Withdrawals and `GET /wallet` never need rates and keep working.
- CNB calls have a connect timeout of 2 s and a read timeout of 3 s. CNB usually answers in about 50 ms,
  and without timeouts a hung connection would block the refresh forever.

**CNB is a trusted source.** Its response is mapped as is, without validating each field. A response
that can't be read fails the refresh just like an outage, so the previous fixing stays in use.

### 4. Money precision and rounding

- **Representation:**
  - Amounts are `BigDecimal` everywhere (never `double`) and strings in JSON.
  - `Funds(currency, amount)` holds an amount at its currency's ISO 4217 minor units, so `7` and `7.00`
    USD are equal.
- **Input rules:**
  - An amount may not have more decimal places than its currency allows: `1.5 JPY` and `1.005 USD` are
    rejected with 400.
  - Amounts are limited to 15 integer and 15 decimal digits, so absurd input is rejected cheaply.
  - Balances are therefore always exact and never rounded.
- **Conversion** keeps full precision (`MathContext.DECIMAL128`).
- **The total rounds once, not per balance.** It converts every balance exactly, sums the results and
  rounds the sum once to the target currency's minor units. The total is a *valuation* at a reference
  rate, not a series of trades. Rounding each balance would add up to one minor unit of error per
  currency, and would make the total depend on how the money is split. (A real exchange would round
  per trade instead.)
- **Rounding is down** (`RoundingMode.DOWN`), so a valuation never shows more money than the wallet
  holds: 11.2585… EUR is shown as 11.25. The mode is one constant, `Funds.ROUNDING_MODE`. `HALF_EVEN`
  would be the choice if the total should be the nearest value instead.

### 5. Concurrency

**Simultaneous withdrawals can never overdraw.** Balances live in a
`ConcurrentHashMap<Currency, BigDecimal>`:

- **A withdrawal** checks the balance and subtracts in one atomic `compute` call for its currency. If
  the funds are insufficient, the call throws and the balance stays unchanged. No explicit lock is
  needed. `InMemoryWalletTest` runs 100 threads, each withdrawing 1 from a balance of 50: exactly 50
  succeed.
- **A deposit** is one atomic `merge`, so no concurrent deposit is lost.
- **A total** reads a snapshot that is consistent for each currency. That is enough, because no
  operation touches two currencies at once.

**Storage sits behind the `Wallet` interface.** Its operations are atomic commands (`deposit`,
`withdraw`, `balances`), not load and save, so no other implementation can reintroduce a
read-then-write race. With a database, a withdrawal would be one conditional statement:
`UPDATE balance SET amount = amount - ? WHERE currency = ? AND amount >= ?`.

### 6. Errors

**Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details object**
(`application/problem+json`). This is the format Spring Boot uses for its own errors, so Spring does
most of the work. Each status means one thing, so a client branches on the status and shows `detail`:

| Status | When |
|---|---|
| 400 | Invalid input: a missing or invalid field (each listed in `errors`), a code that is no ISO 4217 currency (`XYZ`, `usd`), too many decimal places, malformed JSON, a missing or invalid `?currency=` |
| 404 / 405 / 406 / 415 | Unknown path, wrong method, or a non-JSON `Accept` or `Content-Type` (Spring's defaults) |
| 409 | **Insufficient funds**: a withdrawal larger than the balance of that currency, including a currency the wallet doesn't hold |
| 422 | **Unknown currency**: a real currency that can't be valued, because CNB doesn't quote it (e.g. `KES`) or because it is a pseudo-currency (e.g. `XDR`) |
| 503 | A deposit, a total or `GET /exchange-rates` needs rates, but none have been loaded yet |
| 500 | Anything unexpected. The body says only "Unexpected error", and the details are logged. |

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/wallet/deposits",
  "errors": ["amount: must be greater than 0", "currency: must not be null"]
}
```

**Why 409 rather than 422 for insufficient funds:** the request is valid but conflicts with the current
state of the wallet, and the same request can succeed after a deposit.

**How it's built:** `ApiExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`. It adds one
short handler per business exception, a catch-all for 500, and the `errors` list for invalid fields.
Stack traces and parser messages never reach the client. Domain exceptions don't depend on Spring.

**RFC 9457 rather than a custom error DTO**, because Spring already produces it. A custom DTO would mean
re-mapping every one of Spring's own errors.

### 7. CZK

**CZK is the base currency.** CNB quotes every rate against CZK, and its response doesn't list CZK
itself. CZK is in every rate table with an implicit rate of 1, and can be deposited, withdrawn and used
as a total currency like any other. Like every currency, it needs a loaded rate table: with no rates
since startup, CZK deposits and totals are 503 too, while withdrawals still work.

**The domain doesn't hard-wire CZK.** An `FxRateTable` has a `base` currency supplied by the rate
provider, and only `CnbMapper` sets it to CZK.

**Other pairs are converted through the base.** For example, USD → EUR is calculated from two CNB rates.
CNB publishes no official cross rates.

**The supported currencies** are the base plus whatever CNB currently quotes (`GET /exchange-rates`):

- **Deposits** are limited to those, so the wallet never holds money it can't value.
- **Withdrawals** work for any currency held, even if CNB stops quoting it or is down.

The rate source sits behind the `FxRateProvider` interface, like storage behind `Wallet`. CNB is one
implementation (`CnbFxRateProvider`), and a provider with another base could replace it without
touching the wallet.

### Other decisions

- **One implicit wallet.** The assignment describes one user's money. Several wallets would need a
  wallet id in the paths, plus authentication and ownership in front of the API.
- **Resource names:** deposits and withdrawals are collections under the wallet, and rates are at
  `/exchange-rates`.
- **Status codes:** deposits and withdrawals return 200 with the new balances, not 201. Nothing is
  stored that could be fetched later.
- **Currency type:** request DTOs use `java.util.Currency` directly. Jackson and Spring convert it, and
  a bad code is a 400 that names the field.
- **Unknown JSON fields are ignored** ("tolerant reader"). A typo such as `"ammount"` still fails,
  because the real `amount` is then missing.
- **A fully withdrawn currency** stays listed with a zero balance. A total skips zero balances, so it
  needs no rate for them.

## What was skipped or kept small

- **Optional extras:**
  - **AI agents:** no MCP server. The OpenAPI description documents every operation, field and
    status, with examples of the requests and responses, which is enough for an agent; an MCP server would be a thin wrapper around `WalletService`.
  - **Containerization:** done. A two-stage Docker image on Temurin 25, running as an unprivileged
    user.
  - **Observability:** a health endpoint with the CNB state. No structured logging is configured;
    Spring Boot can switch it on without code changes (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`).
- **No holiday calendar** for the CNB refresh (decision 1).
- **Tried in the prototype and removed as more than the assignment needs:**
  - SpotBugs, CodeQL, Dependabot and a GitHub Pages site for the API docs,
  - a k6 load test (`InMemoryWalletTest` covers the concurrency guarantee),
  - `local` and `prod` configuration profiles.

## Known limitations

- **In memory only.** Balances are lost on restart, and the app can't run as several instances.
- **No authentication or ownership.** There is one anonymous wallet.
- **No transaction history or audit trail**, only current balances. A real account would derive
  balances from an append-only ledger.
- **Retries are not idempotent.** A deposit retried after a timeout is applied twice. A production
  version would accept an `Idempotency-Key` header and store the outcome with the balance change.
- **If CNB is down at startup for longer than the retries (about 30 s),** deposits and totals return
  503 until the next successful refresh, at most 10 minutes after CNB is back. In production, the
  instance would report itself not ready until rates are loaded, or start with the last fixing saved
  from its previous run.
- **CNB's response is not validated.** A response the app can't map (e.g. a currency code the JDK
  doesn't know) fails every refresh until the code is changed. The last good fixing stays in use, and
  `/actuator/health` shows the failure.
- **If CNB stops quoting a currency the wallet holds** (as happened with RUB in 2022), totals return 422
  until that currency is withdrawn.
- **Cross rates** are calculated through CZK, not official.

## Project structure

Packages are by feature, with shared code in `common` and Spring wiring in `config`:

```
config/              ClockConfig, SchedulingConfig, SwaggerConfig + ApiDocsProperties, CnbClientConfig + CnbProperties
common/
  money/             Funds: an exact amount in one currency
  exception/         business exceptions, ApiExceptionHandler
  cnb/               CnbClient, DTOs, CnbMapper, CnbFxRateProvider (the cached rates), CnbHealthIndicator
domain/
  wallet/            WalletController, WalletService, WalletMapper, WalletTotal, Wallet, InMemoryWallet, dto/
  fxrate/            ExchangeRatesController, ExchangeRatesMapper, FxRate, FxRateTable, FxRateProvider, dto/
```

- Controllers only validate the request shape and delegate.
- Mappers are the only place where DTOs and domain objects meet.
- The two interfaces, `Wallet` and `FxRateProvider`, are the real extension points. Everything else is
  a concrete class.

## Tests and code quality

| Test | What it covers |
|---|---|
| `FundsTest`, `FxRateTableTest`, `WalletTotalTest` | Money rules, conversion with unit counts, rounding once and down, the assignment's examples |
| `InMemoryWalletTest` | Balances, and 100 concurrent withdrawals against a balance of 50 |
| `WalletServiceTest` | Which operations need rates |
| `CnbClientTest` | HTTP against WireMock with recorded CNB responses, timeouts |
| `CnbMapperTest` | The mapping from CNB's response to a rate table |
| `CnbFxRateProviderTest` | When CNB is called: startup, 14:29 vs 14:30, weekends, retry after a failure |
| `CnbHealthIndicatorTest` | The `cnb` health component |
| `WalletControllerTest`, `ExchangeRatesControllerTest`, `ApiErrorsTest` | The JSON contract and the error responses |
| `WalletEndToEndTest` | The whole app over HTTP, against a fake CNB with a pinned clock, including a retried CNB failure |
| `SchedulingConfigTest` | The refresh is scheduled |
| `OpenApiSpecTest` | Fails if `docs/openapi.yaml` is out of date |

To regenerate `docs/openapi.yaml`:

```bash
./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true
```

`./mvnw verify`, and therefore CI, fails on any of these:

- **Error Prone:** compile-time bug patterns.
- **NullAway with JSpecify:** every package is `@NullMarked`, so anything that may be null must be
  declared `@Nullable`, and this is checked at compile time.
- **javac lint warnings** (`-Werror`).
- **Formatting:** code not formatted with google-java-format (AOSP style) through Spotless. To format
  the code:

  ```bash
  ./mvnw spotless:apply
  ```

- **JaCoCo:** test coverage below 90 % of lines or 85 % of branches.

## Configuration

All settings are in `src/main/resources/application.yaml`. Any of them can be overridden with an
environment variable, e.g. `WALLET_CNB_REFRESH_INTERVAL=5m`. The settings are typed and checked at
startup (`@ConfigurationProperties` with `@Validated`).

| Property | Default | Meaning |
|---|---|---|
| `wallet.cnb.base-url` | `https://api.cnb.cz` | The CNB API root |
| `wallet.cnb.connect-timeout`, `wallet.cnb.read-timeout` | `2s`, `3s` | CNB HTTP timeouts |
| `wallet.cnb.retry-delay` | `2s` | The pause before the first retry of a failed CNB call; it doubles with each of the 4 retries |
| `wallet.cnb.refresh-interval` | `10m` | How often the refresh checks whether a newer fixing can exist |
| `wallet.api-docs.server-url`, `wallet.api-docs.server-description` | `http://localhost:8080`, `Local (…)` | The server shown in Swagger UI and the OpenAPI description |
| `wallet.scheduling.enabled` | `true` | Whether the CNB refresh runs in the background; the test profile switches it off |

Domain rules are constants in the code, not settings: the Prague time zone, the 14:30 publication
time and the ISO minor units.

The Spring Boot tests run with the `test` profile, which points the CNB URL nowhere, so no test can call the real CNB.

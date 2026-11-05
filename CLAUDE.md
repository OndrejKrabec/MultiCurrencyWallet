# Instructions for AI assistants

This is an example project (originally a homework assignment): a small REST API acting as a multicurrency wallet
using Czech National Bank (CNB) exchange rates. Prefer small, well-reasoned code
over features. Every line must be explainable by the author in an interview.

The design and its decisions are in [`docs/design.md`](docs/design.md); follow it, and update it when a
decision changes.

## Stack
- Java 25, Spring Boot 4.1, Maven wrapper (`./mvnw`), springdoc-openapi.
- Tests: JUnit, AssertJ, Mockito, MockMvc, WireMock (standalone) for the CNB API.
- Do not add dependencies without asking.

## Rules
- Money is `BigDecimal`, never `double`/`float`. Amounts travel as JSON strings.
- Validate decimal places against `Currency.getDefaultFractionDigits()`.
- Conversions keep full precision (`MathContext.DECIMAL128`) and round once at the end with `Funds.ROUNDING_MODE`
  (`DOWN`: a value is never shown higher than it is). Never round per balance.
- CNB rates are quoted per `amount` units (100 JPY, 1000 IDR). Never drop the unit count.
- "Today" is the `Europe/Prague` date from an injected `Clock`. Never call `LocalDate.now()` without a clock.
- The domain knows no rate source: `FxRateTable` has a `base` from the provider (CNB: CZK, set only in `CnbMapper`).
  CNB is one `FxRateProvider` implementation in `common/cnb`, swappable like `Wallet`.
- Concurrency: balances live in a `ConcurrentHashMap`; withdrawals use `compute` (atomic check-and-subtract). Never `get` then `put`.
- Errors are RFC 9457 `ProblemDetail` produced in one `@RestControllerAdvice`. Domain exceptions do not depend on Spring.
- Never leak stack traces or internal messages to clients.
- Null safety comes from JSpecify `@NullMarked` + NullAway at compile time: no `Objects.requireNonNull(x, "x")`
  guards on parameters. Use `Objects.requireNonNull(expr)` only to tell NullAway a value can't be null at that point.
- Single implicit wallet, no transaction ledger, no idempotency keys (documented as limitations).

## Code quality bar
- Textbook engineering: modern Java 25 and Spring Boot 4 best practices, KISS, clean OOP
  (behaviour lives with the data it belongs to; no anaemic helpers or static utility grab-bags).
- Static analysis is part of the build and must stay clean (see pom.xml); fix findings, don't suppress them
  without a one-line justification next to the suppression.
- Clean and lean: production-level code, but no speculative features or layers.
- Follow current Java 25 / Spring Boot 4 best practices (records, constructor injection,
  immutable values, configuration properties, no field injection, no static state).
- Leave seams only where extension is realistic and cheap: an interface at a real boundary
  (e.g. the rate source, wallet storage), not around every class.
- Every abstraction must pay for itself today or at the first obvious extension; otherwise inline it.

## Structure
- Package by feature: `domain/<feature>/` holds `XController`, `XService`, `XMapper`, the domain classes, and `dto/`.
- Shared code in `common/` (`money`, `exception`, one package per external service); wiring in `config/`.
- DTOs are records named `*Dto` with full `@Schema` docs; mapping lives only in `*Mapper` components.
- Currencies are `java.util.Currency` in DTOs too (Jackson and Spring convert them; bad codes → 400); annotate
  with `@Schema(type = "string")` so the OpenAPI shows a string.
- Naming: `getX` returns a value or throws, `findX` returns `Optional`;
  computations are `calculateX`; guards are `validateX`; mappers are `toTargetType` (`toWalletTotalResponseDto`).
  Vocabulary: `Funds` (amount + currency), balance (funds held in one currency), total (all balances in one
  currency, `WalletTotal`), `FxRate` (`units` of a currency are worth `rate` in the base). Money in JSON is always
  `{currency, amount}`. No nested data types.
- Booleans are primitive `boolean` (never absent) named with `is`/`has`/`can`, e.g. `hasLastRefreshFailed`.
- Each external service: `common/<service>/` client + DTOs + mapper, `config/<Service>ClientConfig` + properties.
- Every endpoint: `@Operation` (summary + description) and an `@ApiResponse` for each status it can return
  (examples on the request and response schemas; errors are Spring's RFC 9457 Problem Details).
- Configuration: `application.yaml` (+ the `test` profile for the test suite); settings that vary by environment go
  into validated `@ConfigurationProperties` records, domain rules stay constants. Integration tests use
  `@ActiveProfiles("test")`.

## Workflow
- Formatting is google-java-format (AOSP) via Spotless: run `./mvnw spotless:apply` before committing.
- `./mvnw verify` must pass before every commit. Small commits with meaningful messages.
- After an API change, regenerate the committed spec: `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true`.
- Keep `docs/design.md` and the README (API, decisions, tests and structure tables) in sync with the code.
- `AI_USAGE.md` is written by the author: never edit it.

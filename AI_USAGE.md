# AI usage

## Tools and what for

I used **Claude Code** (desktop app, Claude Opus) as the only AI tool, as a coding agent that edits
files, runs the build and calls the API.

- **Prototype:** the first version milestone by milestone:
  - the CNB client with recorded fixtures,
  - money and conversion,
  - the wallet,
  - the REST API and errors,
  - the end-to-end tests,
  - Docker.
- **Reviews:** rounds of parallel read-only reviewer subagents, each with one lens: Spring architect,
  banking security, money and concurrency, test quality, API and docs, and "the interviewer". The agent
  sorted their findings into fixes and rejections.
- **Iteration:** next I went through the code with it part by part. I asked it to explain each
  part, read the sources it quoted (CNB's documentation, Baeldung and RFC 9457), and decided what to
  keep.
- **Research:**
  - CNB's publication time and usage rules,
  - current library versions,
  - JSpecify and NullAway,

## How I steered it

- **[`CLAUDE.md`](CLAUDE.md)** (committed) holds the rules the agent must follow:
  - money is `BigDecimal` and rounds once,
  - the `Clock` is injected,
  - `compute` for withdrawals, never `get` then `put`,
  - RFC 9457 errors,
  - the naming conventions,
  - "every abstraction must pay for itself today".
- **[`docs/design.md`](docs/design.md)** is the blueprint the code has to match, updated with every
  decision - based on the prototype discussion.
- **A quality bar in the prompts:** "textbook engineering, KISS, production-level, but no gold-plating;
  seams only at real boundaries."
- **Guardrails instead of trust:** Error Prone, NullAway, `-Werror`, Spotless and a JaCoCo gate make
  `./mvnw verify` fail. I asked the agent to prove that each analyzer really fires on a deliberately
  broken file.
- **Manual checks:** after each larger change, I had it run the app and `curl` every endpoint and
  error case, against the real CNB, an unreachable CNB and the Docker image.

## Where the AI was wrong or over-engineered

1. **Over-engineering was the main problem.** The prototype was overengineered, it included:
   - a sealed exception hierarchy with an exhaustive switch,
   - problem-type URNs, JSON-pointer error lists with RFC 6901 escaping, and `Retry-After`
     arithmetic,
   - a rate cache with locks, `tryLock`, throttling and a guard against the clock going backwards,
   - all-or-nothing validation of every CNB field - CNB as an untrusted source

   I cut it back step by step. "Let Spring do most of the work" reduced the error handling to one
   short handler per exception. Treating CNB as a trusted source removed 16 validation tests.

2. **Plain mistakes that the guardrails or my questions caught:**
   - **Fixture data from the wrong day:** while CNB was unreachable, the agent hand-wrote "realistic"
     fixtures. Once it could re-record them from the real API, they turned out to contain another
     day's rates.
   - **A rate that lost precision:** the first converter computed a rate, then multiplied by it. The
     rate was already a rounded quotient, so 1 USD came out as 0.999…9 USD. Now there is one
     division at the end.
   - **A contradiction of its own decision:** a CZK-only wallet couldn't get its CZK total while CNB
     was down. Unit tests passed; the manual `curl` pass found it.
   - **Outdated versions:** it pinned GitHub Actions and google-java-format versions from memory, and
     they were outdated. One didn't run on JDK 25.
   - **CI failures in this repository:**
     - A test timeout of 300 ms was too tight for GitHub's runners.
     - The Docker build failed: the JDK image has no `unzip`, so the Maven wrapper downloads a
       `.tar.gz`, which doesn't match the pinned `.zip` checksum.

     Its local checks hadn't built the image. Both failures and their fixes are visible in the
     history.

## What I decided myself

- **The CNB refresh.** I questioned the AI's lazy refresh, had it read CNB's own documentation, and
  chose a scheduled refresh every 10 minutes that calls CNB only on weekdays after 14:30 until the new
  fixing arrives. I skipped a holiday calendar because it isn't worth a dependency, and dropped the
  `isStale` flag in favour of `ratesValidFor`.
- **Rounding.** The total rounds once, not per balance, and rounds **down**, so it never shows more
  than the wallet holds.
- **Errors.** RFC 9457 through Spring's own machinery rather than a custom error DTO, with 409 for
  insufficient funds and 422 for a currency we can't value.
- **CNB as trusted input,** and `java.util.Currency` directly in the DTOs instead of a custom
  validator.
- **API names:** `/wallet/deposits`, `/wallet/withdrawals`, `/exchange-rates` and `baseCurrency`.
- **Naming and structure:** aligned with the codebase I know (`Funds`, `FxRate`, `getX` and `findX`,
  `toTargetType` mappers, package by feature).
- **What to delete:** the extras, the profiles and the load test, and which tests to simplify.
- **The tooling:** Error Prone, NullAway and google-java-format instead of SpotBugs, after weighing
  SonarQube (it needs a server).


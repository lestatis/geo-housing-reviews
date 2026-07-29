# ADR-0009: Adopt Gherkin for endpoint behaviour and PITest as an enforced test-quality gate

Status: Accepted
Date: 2026-07-28
Deciders: Founders
Related: `docs/plans/010-bdd-and-mutation-testing.md`, `.claude/rules/testing.md`, `AGENTS.md` §6

## Context

Asked directly whether the project used TDD or BDD, the answer was no to both. Every chunk delivered
so far had been written test-after, and the only BDD present was behaviour-phrased JUnit method
names. Neither discipline appeared anywhere in `AGENTS.md`, `CONTRIBUTING.md`, `docs/` or
`.claude/rules/`, so nothing was being violated — but nothing was being guaranteed either.

Two gaps follow from that. Endpoint behaviour is expressed only in Java, so nobody outside the code
can check that what we built is what was meant. And "the tests are good" is an assertion with no
measurement behind it: line coverage says a line ran, not that any assertion would notice if it
behaved differently.

## Decision drivers

- The founder must be able to read and correct the platform's behavioural rules — several of them
  (privacy, anti-capture, due process) are product commitments, not implementation details.
- Test quality must be measured, not claimed, if TDD is going to be a real rule rather than a habit.
- Do not introduce tooling without explaining the trade-off (AGENTS.md §3.5).
- Do not spend the project's time rewriting passing tests into new syntax for its own sake.

## Decision

**Gherkin (Cucumber 7.34.6) for endpoint behaviour, at acceptance level only.** Feature files live in
the `app` module and are driven through the real HTTP stack (Spring, Testcontainers, MockMvc). The
domain and application layers keep plain JUnit: unit-level Gherkin is ceremony that costs more to
read than the JUnit it replaces.

**PITest as an enforced gate on the framework-free layers.** Each module carries a mutation-score
threshold; the build fails below it. Infrastructure is excluded — mutants in Spring/JPA glue are
largely equivalent or untestable, and a score dominated by noise is one nobody acts on.

**Migration is on-touch, not a campaign.** All new endpoint behaviour is written as scenarios; an
existing endpoint test class migrates when a chunk next changes that area. The alternative — pausing
feature work to rewrite 114 passing test methods — buys consistency at the price of the roadmap.

**Thresholds are measured floors, not aspirations.** They start at the measured baseline rounded down
to the nearest 5 and are raised when a chunk leaves the score above. A bar nobody can clear gets
switched off, which is worse than a modest one that holds.

## Consequences

### Two findings that constrain how this is built

**The Gradle PITest plugin cannot be used on this project.** `info.solidsoft.pitest:1.15.0`, the
newest release, fails on application under Gradle 9.6.1:

```text
Failed to apply plugin 'info.solidsoft.pitest'.
> Could not get unknown property 'baseDir' for extension 'reporting'
```

Gradle 9 removed `ReportingExtension.baseDir`. PITest is therefore driven from its command-line entry
point (`org.pitest.mutationtest.commandline.MutationCoverageReport`) by a `JavaExec` task in
`geohousing.mutation-testing`. **Do not re-attempt the Gradle plugin** until it publishes Gradle 9
support.

**Cucumber and the PITest JUnit5 plugin work on JUnit Platform 6 only incidentally.** This project
runs JUnit 6.1.1. Cucumber 7.34.6 is built against Jupiter 5.14.2, and `pitest-junit5-plugin` 1.2.3
against platform 1.9.2 (compiled for Java 8). Both nevertheless discover and execute this project's
tests, verified by spike before adoption. That compatibility is not promised by either project, so
**re-verify test discovery on any JUnit, Cucumber or PITest upgrade** — and be specific about the
failure mode to look for: PITest reporting *no tests found* would silently produce a meaningless
score rather than an error.

### Costs accepted

- `./scripts/check.sh` grows by ~55s when the mutation step runs cold (five modules). `-PskipMutation`
  exists for fast local iteration; CI and pre-review runs must not use it.
- The mutation step is *not* what makes the gate slow. Measured on 2026-07-29, the cost is dominated
  by `:app:test`, where 31 of 32 test classes start their own PostgreSQL container. Gradle already
  skips unchanged work correctly — a no-op gate finishes in about a second — so running the gate
  less often saves nothing; the fix is to make container startup overlap. `apps/api/gradle.properties`
  and `maxParallelForks` in `geohousing.java-conventions` do that, and the on-touch migration of
  endpoint tests into the shared Cucumber harness (above) removes containers permanently as it
  proceeds.

  Verified with `./gradlew check --rerun-tasks --no-build-cache` (nothing cached, nothing skipped)
  on 2026-07-29: **1m 45s**, all 35 app suites and 205 tests, plus all five mutation runs. The same
  work took **13m 59s** before. Measure this way or not at all — a plain `--rerun-tasks` still reads
  the build cache, and a gate whose `:app:test` is `UP-TO-DATE` finishes in seconds while proving
  nothing about cost.
- Two ways of expressing endpoint behaviour coexist until on-touch migration completes. This is
  deliberate and time-bounded per area, not permanent.
- Mutation scores can be gamed by assertions that kill mutants without checking meaning. The gate
  raises the floor; it does not replace review.

### Baselines recorded at adoption (2026-07-28)

| Module | Mutations | Killed | Score | Threshold set |
|---|---|---|---|---|
| identity | 126 | 104 | 83% | 80 |
| properties | 103 | 78 | 76% | 75 |
| reviews | 219 | 194 | 89% | 85 |
| verification | 249 | 217 | 87% | 85 |
| moderation | 148 | 113 | 76% | 75 |

The gate was proven non-vacuous two ways before adoption: raising `moderation`'s threshold to 95
failed the build (`Mutation score of 76 is below threshold of 95`), and deleting a single seven-test
class dropped the score from 76% to 55%, also failing.

## Alternatives considered

**Line/branch coverage thresholds (JaCoCo) instead of mutation testing.** Cheaper and better
supported, but it measures execution rather than verification — precisely the gap that motivated
this. PITest immediately found real holes coverage could not: `removed call to touch()` survived on
every `ModerationCase` mutator because nothing asserted `updatedAt` moves, in code with 95% line
coverage.

**Gherkin at every layer.** Rejected: it duplicates the domain's JUnit tests in a slower, wordier
form, and fine-grained negative paths (403-vs-404, malformed cursors) read worse as scenarios.

**Report-only mutation scores.** Rejected: a number that can drift downward unnoticed is
indistinguishable from no number.

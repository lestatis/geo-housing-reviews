# BDD with Gherkin, TDD Enforced by Mutation Testing

Status: Active
Owner: Claude
Related issue: none
Last updated: 2026-07-28

## Objective

Make behaviour readable to non-developers and make test quality measurable, so that "we do TDD" is an
enforced property of the build rather than a claim. See ADR-0009.

## Acceptance criteria

- [x] Every module with domain/application code carries a mutation-score threshold that fails the
      build when breached, wired into `check` so `./scripts/check.sh` enforces it.
- [x] Thresholds are set from measured baselines, and the gate is proven non-vacuous.
- [x] Endpoint behaviour can be expressed as Gherkin scenarios driven through the real HTTP stack,
      with a step-definition library built from the helpers the existing endpoint tests duplicate.
- [x] All five MVP loops have a feature file. Loop 4 ("Report/dispute → resolve safely") arrived
      with plan 009 chunk 6, once reporting had endpoints to describe — 24 scenarios in total.
- [x] `.claude/rules/testing.md` and `AGENTS.md` §6 require test-first for domain/application logic,
      Gherkin for new endpoint behaviour, and the mutation gate.

## Non-goals

- Rewriting the 114 existing endpoint test methods as a campaign; migration is on-touch (ADR-0009).
- Gherkin at domain or application level.
- Mutation testing of infrastructure (Spring, JPA, web adapters).
- Replacing schema, persistence, ArchUnit or boot tests with scenarios.

## Decisions

Recorded in ADR-0009. In brief: acceptance-level Gherkin only; PITest via its CLI because the Gradle
plugin is unusable on Gradle 9; enforced per-module thresholds set from measured floors; on-touch
migration.

## Implementation chunks

1. **Mutation-testing foundation** (this branch): version catalog, the
   `geohousing.mutation-testing` convention plugin, per-module opt-in and thresholds, measured
   baselines, ADR-0009, and the mutation rule in `.claude/rules/testing.md` / `AGENTS.md` §6.
2. **Cucumber foundation and MVP loop features**: dependencies, `AcceptanceTest` suite,
   `AcceptanceWorld`, scenario-scoped state, the step-definition library lifted from the existing
   endpoint-test helpers, and five feature files. Adds the Gherkin rule once the harness exists.
3. **Plan 009 resumes test-first** under the new rules; its endpoint chunks are Gherkin from the
   start, and the `touch()` survivors this work exposed are killed.

## Verification

```bash
cd apps/api
./gradlew :modules:moderation:mutationTest      # fails below threshold
./gradlew check                                 # mutation gate included
cd .. && ./scripts/check.sh
```

## Risks and rollback/forward-fix

Cucumber and `pitest-junit5-plugin` support JUnit Platform 6 only incidentally (ADR-0009); a JUnit,
Cucumber or PITest upgrade must re-verify test discovery, watching specifically for PITest reporting
*no tests found*, which would yield a meaningless score rather than an error. If the mutation step
becomes a drag on the loop, the forward fix is narrowing `targetClasses`, not lowering thresholds —
a threshold that only ever moves down measures nothing.

## Progress log

- 2026-07-28: Plan created after the founder chose to adopt BDD with Gherkin and TDD with PITest.
  Both tools were spiked against this stack before planning: the Gradle PITest plugin was found
  unusable on Gradle 9 (`reporting.baseDir` removed) and the CLI route verified working; Cucumber
  7.34.6 was verified running a scenario through Spring + Testcontainers + MockMvc on JUnit
  Platform 6. Started chunk 1 on `feat/010-mutation-testing-foundation` from clean `main` at
  `b4956ba`.
- 2026-07-28: Chunk 1 implemented. `geohousing.mutation-testing` drives `pitest-command-line` from a
  `JavaExec` task and is applied to the five modules with domain/application code, each with a
  threshold set from its measured baseline; the task is wired into `check`, with `-PskipMutation`
  for local iteration only. `--excludedClasses` stops PITest mutating test classes that share the
  target package — the spike lacked this and reported an inflated 80% for `moderation` against a
  true 76%. Baselines: identity 83% (threshold 80), properties 76% (75), reviews 89% (85),
  verification 87% (85), moderation 76% (75). The gate was proven non-vacuous twice: threshold 95
  against a score of 76 failed the build, and deleting one seven-test class dropped `moderation`
  from 76% to 55% and failed. Cold cost is 55s across five modules; `./scripts/check.sh` passes.
  Ready for independent review.

- 2026-07-28: Chunk 2 implemented on `feat/010-cucumber-acceptance`. `AcceptanceTest` runs every
  feature file against one Spring context and one Postgres container shared by the whole suite;
  `AcceptanceWorld` carries the same stub `JwtDecoder` the JUnit endpoint tests use, so scenarios go
  through the real security chain. `ScenarioState` and `TestApi` are scenario-scoped, because
  Cucumber shares one context across scenarios and a singleton would leak one scenario's actors into
  the next as a phantom flake. Step definitions were grouped by domain (actors, catalogue, reviews,
  trust, moderation, outcomes) rather than the Given/When/Then split the plan sketched — the step
  library grows per feature area, not per keyword, and plan 009 will add reporting steps to it.
  Outcome steps are phrased by meaning rather than status code, so "the content is reported as not
  found" carries the privacy rule a 404 exists to enforce. 16 scenarios across four loops, all
  green. Proven non-vacuous two ways: changing `REVIEW_NOT_FOUND` from 404 to 403 failed exactly the
  scenario asserting a stranger is not told someone's unpublished review exists, and an
  intentionally undefined step failed its scenario rather than being skipped. Both reverted.
  `./scripts/check.sh` passes.

## Final outcome

Not yet complete.

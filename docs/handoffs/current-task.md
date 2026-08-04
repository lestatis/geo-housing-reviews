# Task handoff

## Objective

Implement plan 016 — basic metrics, the last unblocked MVP Must-have.

## Active branch

`feat/016-basic-metrics`, branched from **`fix/015-audit-review-findings`** rather than `main`.
Stacked deliberately: this work moves `windowBounds` out of the audit screen, and that function only
exists on the 015 fix branch. When 015 merges, this rebases onto `main` cleanly. Nothing here
depends on 015 beyond that one shared helper.

**015 is not finished.** Its handoff is preserved at `docs/handoffs/015-audit-review-findings.md`;
it is awaiting an independent review that is blocked on a human running `codex update` (the
installed CLI cannot use its configured model).

## Related issue or plan

No issue. `docs/plans/016-basic-metrics.md`, both chunks implemented.

## Current status

ready_for_review — the fixes passed a fresh independent review with no blocking findings. The
implementation's recorded full gate result remains `EXIT=0`; the reviewer also ran the focused
backend metrics suite and relevant web checks successfully.

## Completed work

### Chunk 1 — the numbers, over HTTP

- `moderation.api.ModerationMetrics`, `reviews.api.ReviewMetrics`,
  `verification.api.VerificationMetrics`, each with a small record for throughput. Three named
  interfaces rather than one shared port, because each module answers a *different* question — a
  common interface would be a shape they do not share.
- Each adapter issues a `COUNT` in the database rather than counting a loaded list: a backlog worth
  measuring is a backlog too big to load.
- `AdminMetricsService` composes them; `GET /api/admin/metrics?since=&until=` behind the existing
  admin gate. **No migration** — every number comes from a column that already existed.
- Throughput reads the append-only audit tables, not current state. A review published on Monday and
  removed on Friday is one publication that really happened, and today's status would erase it.
- Five Gherkin scenarios and `AdminMetricsIntegrationTest` for the boundary cases.

### Chunk 2 — the screen

- `/metrics`: a "right now" queue block and a windowed throughput block, linked from the moderation
  nav beside Audit.
- The overturn line is **counts first, rate second** — "3 of 14 overturned (21%)". A bare percentage
  over a handful of appeals reads as a finding about the platform rather than the small number it
  is. An empty window says *no appeals heard*, never 0%, because 0% is a claim about outcomes that
  nobody has made.
- `describeWindow` and `windowBounds` moved to `apps/web/src/window.ts`, shared with the audit
  screen, with `defaultDays` passed by the caller: the counting rule is shared, the span is not
  (a week versus a month).
- The screen states that these are platform totals and points at the audit timeline for per-person
  questions — the deliberate decision, visible rather than discovered.

## Remaining work

No implementation work remains for this branch. It is ready for the human to decide whether to
merge; agents must not merge automatically. Right of reply remains the only MVP Must-have and stays
blocked on representative claims (`P-013`).

## Decisions made

- **Moderation and trust health, not growth.** `docs/MODERATION.md` line 83 is the only measurement
  the documents name — "measure overturned decisions" — and it measures fairness, not volume.
- **No per-moderator breakdown.** Decisions-per-day rewards deciding fast, and an overturn rate over
  a handful of appeals reads as a competence score. The audit timeline already answers "what has
  this moderator been doing", with a stated purpose and a stated risk; a dashboard would turn the
  same data into a leaderboard.
- **The `analytics` module stays empty.** Everything here is computed on read from data other
  modules own. A module that stored nothing and depended on four others would add edges to the
  graph and own nothing. It gets a reason to exist when something genuinely stores rollups.
- **Reading metrics is not audited**, unlike reading the audit log. It names nobody and exposes no
  personal data, so there is nothing for an access review to review, and recording every dashboard
  load would bury the `VIEW_ACCOUNT` and `VIEW_AUDIT` rows access review depends on. This is a
  deliberate exception to `.claude/rules/security.md`; the cheap reversal is identity `V2.10` plus a
  `VIEW_METRICS` action, so a reviewer can overrule it easily.

## Changed files

New: three `api/*Metrics.java` + throughput records, three `Jpa*Metrics.java` adapters,
`app/.../metrics/{AdminMetrics,AdminMetricsService,AdminMetricsController,AdminMetricsExceptionHandler,MetricsBeanConfiguration}.java`,
`AdminMetricsServiceTest`, `AdminMetricsIntegrationTest`, `acceptance/MetricsSteps.java`,
`apps/web/src/window.ts` + test, `apps/web/src/metrics/metrics.ts` + test,
`apps/web/app/metrics/page.tsx`, `apps/web/e2e/metrics.spec.ts`, `docs/plans/016-basic-metrics.md`.

Modified: four `SpringData*Repository` interfaces (count queries), `moderate-and-administer.feature`,
`docs/api/openapi.json`, `apps/web/src/audit/timeline.ts` (window helpers removed),
`apps/web/app/audit/page.tsx`, `apps/web/app/moderation/page.tsx` (nav).

## Commands and tests

```bash
cd apps/api && ./gradlew :app:test --tests '*AdminMetrics*' -PskipMutation
cd apps/api && ./gradlew :app:test --tests '*OpenApiContractIntegrationTest' -DupdateOpenApiSpec=true
cd apps/web && pnpm test && pnpm typecheck && pnpm lint
./scripts/check.sh          # read the EXIT= marker
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
```

Three guards proved by breaking them:

1. Counting `UPHELD` instead of `OVERTURNED` fails exactly the scenario asserting the overturn count.
2. A metrics adapter reaching into another module is caught **twice** — Gradle's module graph rejects
   a non-dependency at compile time, and `ModuleBoundaryArchitectureTest` catches reaching past `api`
   where the dependency does exist.
3. The half-open window is pinned by decisions placed at the inclusive start, the last instant
   inside, and the exclusive end.

## Second review's findings, all three fixed

### P1 — outcome metrics counted failed attempts — FIXED

Confirmed before changing anything: `ReviewModerationOutcome` and `VerificationDecisionOutcome` both
carry `APPLIED` and `NOT_FOUND`, and both count queries filtered on action and time alone. A
moderator publishing a review that no longer exists would have counted as a publication.

Both queries now filter `outcome = APPLIED`. `anAttemptThatFoundNothingIsNotWorkCompleted` places
`NOT_FOUND` rows in the window and asserts none of the four displayed totals move; neutralising the
filter fails exactly that test and nothing else.

### P1 — appeal counts could observe two snapshots — FIXED

Confirmed, and the consequence was worse than a wrong number. At READ COMMITTED each statement takes
its own snapshot, so an appeal decided between the two counts yields one overturned out of zero
heard; `ModerationThroughput` refuses that impossible pair; and the advice reported it as *"the
window ends before it starts"*. A routine appeal decision could break the screen and blame the
reader's dates.

Both numbers now come from **one grouped query** (`group by a.status`). "Overturned ≤ heard" is
structural — every overturned appeal is one of the rows being summed — rather than an invariant
checked afterwards and violated by ordinary use. Widening the isolation level would have been the
other option; grouping removes the race instead of tolerating it.

**Worth recording against my own earlier claim.** In the previous chunk I defended that
`ModerationThroughput` guard as protection against a bad predicate. It is, and it stays. What I
missed is that it also converted a benign race into a user-visible failure. Both things were true.

### P2 — the advice caught too much — FIXED

`InvalidMetricsWindowException` now, handled alone. Catching bare `IllegalArgumentException` meant
any internal fault reached an administrator as a complaint about their input, sending them to fix
dates that were never wrong while the real fault went unreported.

## Fresh independent review — no blocking findings

The reviewer inspected commit `63a6f79` and the full branch diff against `main` after the fixes.

- Review and verification throughput now require `outcome = APPLIED`; the integration test places
  both applied and `NOT_FOUND` rows in the window and proves only real effects count.
- Appeal outcome totals now come from one grouped query, so the count of overturned appeals is
  structurally a subset of all heard appeals in a single statement snapshot.
- The controller advice now handles only `InvalidMetricsWindowException`; unexpected internal
  failures are no longer translated to a misleading invalid-window response.

No new authorization, privacy, module-boundary, data-exposure, or concurrency issue was found.

Fresh reviewer checks:

```bash
cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.metrics.*' -PskipMutation --no-daemon --max-workers=1 --console=plain
# BUILD SUCCESSFUL (15s)

cd apps/web && pnpm vitest run src/window.test.ts src/metrics/metrics.test.ts
cd apps/web && pnpm typecheck
cd apps/web && pnpm lint
# 15 tests passed; typecheck and lint passed
```

## Earlier findings, for the record

### P1 — outcome metrics count failed moderation and verification attempts

`JpaReviewMetrics` and `JpaVerificationMetrics` ask their audit repositories to count by action
only. The new JPQL predicates likewise filter only `action` and time. Those same append-only tables
explicitly store `NOT_FOUND` attempts: a moderator acting on a review or verification case that has
been deleted still creates an auditable event, but no review was published or removed and no
verification was approved or rejected. Consequently, an administrator can see inflated throughput
and conclude that moderation completed work it did not complete.

Add an `outcome = APPLIED` predicate to both count queries and tests that place a `NOT_FOUND` row in
the window; it must not change any of the four displayed outcome totals.

### P1 — appeal counts can observe different snapshots during a normal decision

`JpaModerationMetrics.between` issues `countDecidedBetween` and then
`countWithStatusDecidedBetween` as separate SQL statements. At PostgreSQL's default read-committed
isolation, a concurrent transaction changing an appeal from `PENDING` to `OVERTURNED` can commit
between them. The first count then sees zero heard appeals and the second sees one overturned
appeal. `ModerationThroughput` correctly refuses that impossible pair, but the handler catches the
resulting `IllegalArgumentException` and returns the false message that the request window is
invalid. A routine appeal decision can therefore make the metrics screen fail to load.

Return both appeal counts from one aggregate query (for example `COUNT(*)` plus conditional count)
or use one explicit consistent snapshot, and have the web advice handle only a dedicated invalid
window exception. Add a concurrent/snapshot regression test or a repository test that establishes
the single-query aggregate.

The independent reviewer ran successfully:

```bash
cd apps/web && pnpm vitest run src/window.test.ts src/metrics/metrics.test.ts
cd apps/web && pnpm typecheck
cd apps/web && pnpm lint
```

The full backend gate was not rerun by the reviewer. Four earlier implementation notes worth
carrying forward:

- **The schema caught the test fixture three times.** A `CLOSED` case needs a `closed_at`; an adverse
  decision needs a public explanation ("no takedown without telling the author why" is a `CHECK`, not
  a convention); a decided appeal needs an outcome explanation. Fixed by writing honest rows, not by
  working around the constraints.
- **Generated TypeScript typed two nullable fields as non-nullable.** `oldestOpenCaseAgeDays` and
  `appealOverturnPercentage` return null, and null-versus-zero is the whole point of both. Marked
  `@Schema(nullable = true)` and regenerated rather than loosening the tests.
- **Gradle's incremental compile silently skipped a new file.** `:app:compileJava` reported BUILD
  SUCCESSFUL without producing `AdminMetricsExceptionHandler.class`, so the advice was never
  registered and a scenario failed with a 500. `--rerun-tasks` fixed it. A green compile is not
  proof that a new class exists.
- **Two top-level classes in one file** failed `checkstyleMain`. Caught by the gate, since split.

## Unresolved risks

- **A number on a screen becomes a target.** "Open cases: 12" invites clearing the queue rather than
  deciding well. Platform-level only is the mitigation; if per-moderator numbers are ever added, the
  trade-off should be re-argued rather than inherited.
- **Roughly ten `COUNT`s per page load.** Fine at current volume, against indexed columns. The first
  shape to revisit if the tables grow — and the point at which `analytics` would earn its existence.
- **An overturn rate over few appeals still misleads if read alone.** The counts lead the line for
  that reason, but a reader determined to quote the percentage can.

## Next action

Human review and merge decision. Do not merge automatically. 015's review is still outstanding and
blocked on a human `codex update`.

## Last updated

2026-08-04

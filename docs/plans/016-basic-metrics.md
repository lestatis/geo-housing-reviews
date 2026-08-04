# Plan 016 — basic metrics

## Context

`docs/PRD_MVP.md` §5.8 lists "basic metrics" among the admin interface's contents, and
`docs/MVP_SCOPE.md` counts it a Must-have. It is the last unblocked one: right of reply stays
blocked on representative claims (`P-013`), and export/deletion already ships.

`docs/MODERATION.md` line 83 is the only place the documents say what to measure — **"measure
overturned decisions"**. That is a fairness measurement, not a volume one, and it sets the tone for
the rest: the question an administrator needs answered is *is moderation keeping up, and is it
getting it right*, not *how big are we*.

Plan 015 made the audit log readable. This is the other half of the same need: the audit log answers
"what happened, by whom"; metrics answer "is the system healthy", without naming anybody.

**Outcome:** an administrator opens one screen and can tell whether the queues are being served and
whether appeals are overturning decisions.

## What it shows

Two blocks, both platform-level.

**Right now** — from current state:

| Number | Source |
| --- | --- |
| Open moderation cases | `moderation.moderation_case` where status is not terminal |
| Reviews awaiting moderation | `reviews.review` at `PENDING_MODERATION` |
| Verifications pending | `verification.verification_case` at `PENDING` |
| Age of the oldest open case | `min(opened_at)` over open cases |

**In the window** (default: the last 30 days) — from the append-only tables:

| Number | Source |
| --- | --- |
| Moderation decisions made | `moderation.moderation_decision.decided_at` |
| Appeals heard, and how many overturned | `moderation.appeal` decided in window, `status = 'OVERTURNED'` |
| Verifications approved / rejected | `verification.verification_decision_audit_event` |
| Reviews published / removed | `reviews.review_moderation_audit_event` |

**Why throughput comes from the audit tables and not from current state.** A review published on
Monday and removed on Friday is one publication that really happened; counting today's statuses
would erase it. The audit tables are append-only precisely so that what happened stays true, which
makes them the honest source for "what was done in this window" — and a second real use for the rows
plan 015 exposed.

## Approach

**Each module answers for itself, in its own `api` package.** `reviews.api.ReviewMetrics`,
`moderation.api.ModerationMetrics`, `verification.api.VerificationMetrics` — small interfaces with
named methods returning small records.

**Not a shared port in `shared-kernel`.** `AuditTrail` earned its place there because five modules
answer the *same* question and the app collects them polymorphically. Here each module answers a
*different* question; a common interface would be a shape they do not share. Plan 015's own risk
section said a reviewer should push back hard on the second type wanting to live in `shared-kernel`
— this is that second type, and the answer is no.

**Not the `analytics` module.** It is scaffolded and empty, and it stays that way. Every number here
is computed on read from data other modules own; a module that stored nothing but depended on four
others would add edges to the dependency graph and own nothing. The app is already the one place
entitled to hold every module at once — the same reasoning that put the audit merge there. When
something genuinely *stores* analytics (recorded events, rollups), the module gets a reason to exist.

**`GET /api/admin/metrics?since=&until=`**, behind the existing `/api/admin/**` gate, returning one
flat object. No pagination, no cursor: it is a fixed, small answer.

**The window translation is shared with the audit screen.** `describeWindow` and `windowBounds` move
from `apps/web/src/audit/timeline.ts` to `apps/web/src/window.ts`, and both screens import them. Two
concrete callers, and the alternative is two copies of the inclusive-date → half-open rule that a
review already caught getting wrong once.

## Deliberately not included

- **No per-moderator breakdown.** A screen ranking moderators by decisions-per-day rewards deciding
  fast, and a per-moderator overturn rate computed from a handful of appeals reads as a competence
  score. The audit timeline already answers "what has this moderator been doing" — a named purpose
  (access review) with a named risk. A dashboard turns the same data into a leaderboard.
- **No growth counts.** Properties and reviews created per week invite no decision an administrator
  can act on today.
- **Nothing from evidence or moderator notes.** `SECURITY_PRIVACY.md` §5 forbids private moderation
  notes in analytics outright.
- **No migration.** Every number comes from a column that already exists.

## One decision to flag

**Reading metrics is not audited**, and `.claude/rules/security.md` says "new admin actions require
audit events". The exception is deliberate: the screen exposes no personal data and no individual's
activity, so there is nothing for an access review to review — and recording every dashboard load
would bury the `VIEW_ACCOUNT` and `VIEW_AUDIT` rows that access review actually depends on. The
cheap alternative, if a reviewer disagrees, is identity migration `V2.10` widening the action check
plus a `VIEW_METRICS` action. It is a small change, so this is easy to overrule.

## Chunks

### 1. The numbers, over HTTP

Three `api` ports and their adapters (a `COUNT` query each, not a list that is then counted), the
composing service, `GET /api/admin/metrics`, Gherkin scenarios.

Test-first: each port's counting behaviour against a real database — including the cases that are
easy to get wrong (a `CLOSED` case is not open; a `PENDING` appeal is not "heard"; a decision at the
window's exclusive upper bound is outside it).

### 2. The screen

`apps/web/app/metrics/page.tsx`, linked from the moderation nav beside Audit. Reuses
`src/window.ts`. Playwright: seed a decided appeal that was overturned, then find the overturn rate
on the screen — the one number the documents actually asked for, end to end.

## Critical files

| What | Path |
| --- | --- |
| Ports (new) | `modules/{reviews,moderation,verification}/src/main/java/.../api/*Metrics.java` |
| Adapters (new) | each module's `infrastructure/persistence/Jpa*Metrics.java` |
| Count queries | the existing `SpringData*Repository` interfaces, e.g. `SpringDataModerationDecisionRepository` |
| Compose + endpoint (new) | `app/src/main/java/com/example/geohousing/app/metrics/` |
| Window helpers (moved) | `apps/web/src/audit/timeline.ts` → `apps/web/src/window.ts` |
| Screen (new) | `apps/web/app/metrics/page.tsx` |

## Verification

```bash
cd apps/api && ./gradlew :app:test --tests '*Metric*' -PskipMutation
./scripts/check.sh          # read the EXIT= marker
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
```

Three things shown rather than assumed:

1. **The overturn rate counts overturns** — flip the adapter to count every decided appeal and the
   scenario asserting the rate fails.
2. **The window is half-open at both layers** — a decision recorded at the window's exclusive end is
   outside it, proved by a test at that exact instant, as the audit fix pass established.
3. **The boundary holds** — make a metrics adapter read another module's table and
   `ModuleBoundaryArchitectureTest` fails.

## Risks

- **A number on a screen becomes a target.** "Open cases: 12" invites clearing the queue rather than
  deciding well. Platform-level only is what this plan does about it; if a later chunk adds
  per-moderator numbers, that trade-off should be re-argued rather than inherited.
- **Counts are computed on every load** — roughly ten `COUNT`s per request, against indexed columns
  at current volume. The first shape to revisit if it grows, and the point at which the empty
  `analytics` module would finally have a reason to exist.
- **An overturn rate over a handful of appeals reads as more than it is.** The screen shows the
  counts alongside the percentage, so "1 of 3" cannot be read as "33% of something large".

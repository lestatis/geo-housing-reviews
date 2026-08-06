# Task handoff

## Objective

Fix the P2 from the independent review of the plan 015 audit fix pass: a `nextCursor` from an
actor-filtered timeline could not be sent back on its own.

## Active branch

`fix/015-cursor-actor-inheritance`, branched from `main`.

**`main` already contains the work this fixes.** Plans 015 and 016, including the audit fix pass
(`d5087c3`, `7dac5f5`), were merged before that fix pass had been independently reviewed. The review
has now happened — see below — and this branch carries its one finding.

## Related issue or plan

No issue. Follows `docs/plans/015-audit-log-readable.md`; `docs/handoffs/015-audit-review-findings.md`
holds that plan's history.

## Current status

in_progress — the first review's P2 and both of the **second** review's P2s are implemented.
`./scripts/check.sh` reports `EXIT=0`; 37/37 Playwright.

## The review

Run after `codex update` (0.146.1). `--base main` would have shown an empty diff because the work
was already merged, so the range reviewed was
`feat/015-audit-screen-chunk2..fix/015-audit-review-findings` — exactly the two fix-pass commits.

**One P2, no P1s.** No authorization, privacy, module-boundary or data-exposure issue found.

## Completed work

### P2 — a cursor could not be sent back on its own

The finding was right, and the contradiction was mine: `AuditPageToken`'s javadoc said "a null bound
or actor means the request did not restate it … the value is taken from it", while the code
inherited omitted *dates* and refused an omitted *actor*. A client handed a `nextCursor` from a
filtered timeline therefore had to reconstruct the query to use it, which makes it a fragment rather
than a continuation — and `API_GUIDELINES.md` §Pagination asks for cursor pagination that works.

**The obvious fix would have reintroduced the failure this plan exists to prevent.** The screen
labelled its page from the *request*, so inheriting the actor silently means a URL carrying only a
cursor shows one administrator's actions under the word "everyone" — under-reporting in an audit
log, which is the exact misreading plan 015 was written to stop. The reviewer did not name this
half.

So both halves changed:

- `verifyContinues` treats omission as inheritance for the actor as for the bounds. Naming a
  *different* actor is still `NOT_FROM_THIS_QUERY`. Silence inherits; contradiction does not.
- `AuditTimelineResponse` gained `appliedActorAccountId` — whose actions the page actually shows —
  and the screen states that instead of guessing from the URL. The "show older" link and the filter
  box read from it too, so a trimmed link repairs itself rather than compounding.

## Second review of this branch — two more P2s, both fixed

### P2 — a cursor-only link died overnight

The screen always re-derived a window and sent it. Opened after UTC midnight, a `?cursor=…` URL
therefore sends a *different* window from the one its token was issued for, the API refuses the
contradiction, and the link is dead. **My e2e claimed "only the cursor remains" but passed only
because a same-day default happened to equal the token's window** — the test was weaker than its
name, which the reviewer spotted and I had not.

A continuation now sends no window and no actor at all: the token owns the query. The response
carries an `applied` object (`since`, `until`, `actorAccountId`) and the screen renders that, via
`windowFromBounds` — the tested inverse of `windowBounds`, so the page states a window it was told
rather than one it guessed. The e2e issues its cursor from an explicitly non-default window, so
today's default cannot match by coincidence and the overnight path is exercised on any day.

Proved non-vacuous: restoring the old "always re-derive" line fails exactly that test and nothing
else.

### P2 — a nullable field declared non-nullable

`appliedActorAccountId` is null for an unfiltered page while the schema said `type: string`. Correct,
and **this is the second time on this branch family** — the metrics fields hit the same thing, I
fixed them with `@Schema(nullable = true)` and wrote the lesson into a handoff, then added a new
nullable field without it. Both are now `["string","null"]` in the regenerated spec.

## Remaining work

Commit, then a third independent review before merge.

## Decisions made

- **Silence inherits, contradiction is refused.** The token is the statement of which query is being
  continued; a request that says nothing is not disagreeing with it.
- **The response states the applied filter rather than the screen inferring it.** The alternative —
  keeping the strict rejection and rewriting the javadoc to match — would have left the cursor
  unusable on its own for API clients, and `API_GUIDELINES.md` asks otherwise. Echoing one field is
  cheaper than that, and it makes the screen's "always state what you are showing" promise true even
  for a page reached by cursor alone.

## Changed files

Modified: `app/.../audit/{AuditPageToken,AuditTimelineResponse,AuditController}.java`,
`AuditPageTokenTest`, `AuditPagingIntegrationTest`, `docs/api/openapi.json`,
`apps/web/app/audit/page.tsx`, `apps/web/e2e/audit.spec.ts`.

## Commands and tests

```bash
cd apps/api && ./gradlew :app:test --tests '*Audit*' -PskipMutation
cd apps/api && ./gradlew :app:test --tests '*OpenApiContractIntegrationTest' -DupdateOpenApiSpec=true
cd apps/web && pnpm typecheck && pnpm lint
./scripts/check.sh          # read the EXIT= marker
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
```

Three levels of cover for one behaviour, because it spans three:

1. `aCursorCanBeSentBackOnItsOwn` and `namingADifferentActorIsStillRefused` — the rule itself.
2. `aCursorFromAFilteredTimelineCanBeSentBackAlone` — over HTTP, with no window and no actor,
   asserting both the rows and `appliedActorAccountId` survive the trim.
3. An e2e that strips a paged link down to its cursor and checks the screen still names whose
   actions it is showing.

## Failures and blockers

None open.

## Unresolved risks

- **`main` carries ten branches that were merged without an independent review.** This round shows
  the step is load-bearing rather than ceremonial: it found a real defect that shipped. Worth a
  deliberate decision about whether merges wait for it.
- **`@Schema(requiredMode = REQUIRED)` does not reach the generated spec.** Verified, not assumed:
  `applied` is still optional in the generated client type. The screen therefore declines to render
  when it is missing rather than falling back to a guessed window — a mislabelled audit page is
  worse than a missing one. Worth understanding why springdoc ignores it before relying on the
  annotation anywhere else.
- Unchanged: wedged Docker containers needing a privileged `systemctl restart docker`; no agent can
  clear them.

## Next action

Independent review of this branch, then a human merge decision. Right of reply remains the only MVP
Must-have and stays blocked on representative claims (`P-013`).

## Last updated

2026-08-04

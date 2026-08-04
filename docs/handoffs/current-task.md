# Task handoff

## Objective

Fix the four findings from the independent review of plan 015 (the audit timeline).

## Active branch

`fix/015-audit-review-findings`, branched from `main` at `a3c7d69`. Plan 015's two chunks are
already on `main` and `origin/main`; this branch changes only what the review asked for.

## Related issue or plan

No issue. `docs/plans/015-audit-log-readable.md` — the plan is complete; this is its fix pass.

## Current status

in_progress — the second independent review's blocking finding is fixed and covered.
`./scripts/check.sh` reports `EXIT=0` and all 31 Playwright tests pass. Awaiting a third review.

## Completed work

### P1a — the timeline can be read past its first page

`API_GUIDELINES.md` §Pagination requires cursor pagination and stable sort semantics for audit
events; the review was right that "no deep paging" was never mine to declare out of scope.

- `shared-kernel` gains `AuditCursor(at, module, id)` — a position in the merged order, encoded
  base64url and opaque to callers. An unreadable cursor is **refused**, not treated as a first page:
  showing somebody the same page again would let them believe they had reached the end of a list
  they had not.
- `AuditEntry` carries the source row's `id`, which makes `NEWEST_FIRST` a **total** order
  (`at` desc, `module` asc, `id` desc). Without a total order a page boundary is free to repeat one
  entry and drop another.
- `AuditCursor.idBoundFor(module)` turns that global order into a predicate each source can push
  into SQL knowing only its own name: its own module resumes at its own row, a module sorting after
  it has not been read at this instant at all, one sorting before it is finished with this instant.
- All five queries are keyset now: `at >= :from and (at < :beforeAt or (at = :beforeAt and
  id < :beforeId))`.
- `AuditTimelineService` asks each trail for `limit + 1`. That extra row is the whole test for "is
  there more": with exactly `limit`, a full merge could equally mean the history ended or that one
  trail was cut off mid-answer.
- `AuditTimelineResponse` gains `nextCursor` (null on the last page). `/audit` shows **"more remain"
  vs "this is the end of the window"** in the caption and a *Show older entries* link.

**`UUID.compareTo` was wrong here, and the test found it.** It compares the two halves as *signed*
longs, so every id with the high bit set — about half of all random ids — sorts below every id
without it. PostgreSQL compares `uuid` as sixteen unsigned bytes. The keyset predicate is evaluated
by Postgres and the merge is evaluated in Java, so the two disagreeing meant rows quietly missing at
a page boundary. `AuditCursor.ID_ORDER` is the unsigned comparator, and it is what `NEWEST_FIRST`
uses.

### P1b — an inclusive end date no longer drops the last second of the day

`windowBounds` translates the screen's two inclusive UTC dates into the half-open range the API
takes: the end date becomes the **start of the next day**. The screen previously sent `23:59:59Z`
against SQL asking for `created_at < :until`, so anything in the final sliver of the chosen day
vanished from an audit log. Beyond year 9999 an ISO instant grows a sign and a fifth digit, so the
translation clamps at `9999-12-31T23:59:59.999Z` — nothing can be recorded after it.

### P2a — the window semantic is now one thing, stated

Two **inclusive UTC calendar dates**, which is what the date inputs mean to whoever types them. The
default is today plus the six days before it — seven inclusive days, matching what the screen says.
It used to subtract seven and show eight dated days. The screen says "both days included". A `since`
or `until` that is not a calendar date falls back to the default rather than being passed on: a
rejected query tells the reader nothing and a mangled one tells them something false.

### P2b — invalid queries are 400s that name the parameter

- `InvalidAuditQueryException(field, code, detail)` and an `AuditExceptionHandler` scoped to
  `app.audit` (a global advice would answer for other modules' controllers). Malformed actor id,
  reversed window and unreadable cursor are RFC 7807 with `code: INVALID_AUDIT_QUERY` and
  `fieldErrors[].field`.
- Documented in OpenAPI. **Both** responses had to be declared: adding one `@ApiResponse` replaces
  springdoc's derived set rather than adding to it, and a documented 400 that silently deleted the
  200 would take the response schema out of the generated client.
- The screen shows a targeted message per field. A mistyped account id used to come back a 500 and
  read as "the timeline could not be loaded" — telling an administrator the audit log was broken
  when they had fumbled a paste.

## Remaining work

A fresh independent review of the cursor-binding fix.

Of the MVP Must-haves, right of reply (blocked on representative claims per `P-013`) and basic
analytics remain. Eight branches are on `main` with no independent review — still awaiting a
decision.

## Decisions made

- **The cursor carries a module and an id, not just an instant.** Five sources can record in the
  same millisecond; with a timestamp alone a page ending mid-millisecond either repeats that instant
  or skips the rest of it, and skipping is how an audit log loses the row somebody is looking for.
- **A cursor from outside the requested window is refused.** A cursor names a position, not a
  window; pairing one with a window it did not come from would answer a question nobody asked.
- **The screen's dates are inclusive; the API's `until` is exclusive.** One translation, in one
  function, tested.
- **`limit + 1` over-fetch per trail** rather than a count query — five counts per page to answer a
  yes/no question the extra row already answers.

## Changed files

New: `shared-kernel/.../audit/AuditCursor.java` + test, `app/.../audit/AuditPage.java`,
`InvalidAuditQueryException.java`, `AuditExceptionHandler.java`, `AuditPagingIntegrationTest.java`.

Modified: `AuditEntry`, `AuditTrail`, five `Jpa*AuditTrail`, five Spring Data repositories, three
audit entities (`id()` accessors), `AuditQuery`, `AuditTimelineService`, `AuditController`,
`AuditTimelineResponse`, `AuditTimelineServiceTest`, `AuditSteps`, `ScenarioState`,
`moderate-and-administer.feature`, `docs/api/openapi.json`, `apps/web/app/audit/page.tsx`,
`apps/web/src/audit/timeline.ts` + test, `apps/web/e2e/{audit.spec.ts,seed.ts}`.

## Commands and tests

```bash
cd apps/api && ./gradlew :app:test --tests '*Audit*' -PskipMutation
cd apps/api && ./gradlew :app:test --tests '*OpenApiContractIntegrationTest' -DupdateOpenApiSpec=true
cd apps/web && pnpm vitest run src/audit/timeline.test.ts && pnpm typecheck && pnpm lint
./scripts/check.sh          # read the EXIT= marker, not a wrapper's status
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
```

**The paging guard was proved by breaking it.** Reverting `NEWEST_FIRST` to `UUID.compareTo`
(signed) makes `AuditPagingIntegrationTest.walkingALongTimelineReadsEveryEntryExactlyOnce` fail and
nothing else — which is what that test exists to catch. The integration test inserts sixty identity
rows, three per second, then three rows sharing one instant across two modules with ids on either
side of the signed/unsigned boundary, and asserts that reading seven at a time returns exactly what
one page of the same window returns.

## Second review's findings, both fixed

### P1 — continuation cursors were not bound to their query (blocking) — FIXED

`AuditPageToken` now carries the position *and* the normalized `since`, `until` and actor. Restating
them on the request is optional; asking for different ones is `INVALID_AUDIT_QUERY` on `cursor` with
code `NOT_FROM_THIS_QUERY`. Dropping the actor counts as disagreement too — "everyone" is a filter,
so continuing an unfiltered timeline while asking about one person is as wrong as the reverse.

Taking the bounds *from* the token also removed a latent trap the review did not name: with the
window read from the request, a cursor issued against the **default** window could never have
validated, because that default is computed from `now` on every call. A continuation is now
well-defined for a caller who never stated a window.

The wire format moved out of `shared-kernel` into `app/audit`: which query a page belongs to is an
application concern, and `AuditCursor` is now only a place in an ordering — which is all any
module's adapter needs.

**No HMAC, deliberately.** The review suggested integrity protection "preferably". The token carries
no authority: the endpoint is `ADMIN`-gated and anything a forged token can express is a query the
same caller could simply send. A signing key would add key management and rotation without adding a
property worth having. Reopen this if the token ever starts carrying something a caller could not
ask for directly.

Proved non-vacuous: commenting out `verifyContinues` fails `aCursorCarriedOntoADifferentSearchIsRefused`
and nothing else.

### P2 — overflow calendar dates were accepted by the screen — FIXED

Measured rather than assumed: JavaScript rejects `2026-13-45` and `2026-01-32` but **normalizes**
`2026-02-31` to 3 March and `2026-04-31` to 1 May. The screen would have displayed "2026-02-31"
while asking the API for a window ending 4 March. `asCalendarDate` now round-trips the parse against
the input; a real leap day still passes.

## The original finding text, for the record

### P1 — continuation cursors are not bound to their query (blocking)

`AuditCursor` encodes only `(at, module, id)`, and `AuditQuery` merely checks whether `at` lies
inside the newly supplied window. It carries and checks neither the actor filter nor the exact
`since`/`until` bounds. Reusing a valid cursor from an actor-A timeline for actor B, or for a
different enclosing window, resumes after the old position. It therefore drops entries newer than
that position and can return `nextCursor: null`, falsely telling an administrator they reached the
end of the requested audit history.

Bind the normalized query identity (`since`, `until`, actor) to the continuation token, preferably
with integrity protection, and reject mismatches as `INVALID_AUDIT_QUERY` on `cursor`. Cover both
filter and window changes in an HTTP integration test.

### P2 — overflow calendar dates are not rejected by the screen (non-blocking)

`asCalendarDate` checks only the `YYYY-MM-DD` shape and whether JavaScript can construct a `Date`.
JavaScript normalizes values such as `2026-02-31`, so the function returns the invalid original
text and `windowBounds` sends an invalid instant to the API. Verify that parsing round-trips to the
same ISO calendar date before accepting it, and add a unit test.

The independent reviewer ran the web timeline unit tests, typecheck, and lint successfully. A
scoped backend audit-test rerun was stopped after its Gradle/Testcontainers process did not finish;
do not treat that rerun as fresh verification. Existing committed test reports are passing, but
they do not cover the P1 misuse case.

## Unresolved risks

- **The timeline concentrates what was scattered.** Admin-only and audited is what the platform can
  do about "what has this moderator been doing" being one screen; naming it so a reviewer weighs it.
- **Five queries per page, now `limit + 1` rows each.** Fine at current volume; the first shape to
  revisit if the audit tables grow.
- **No retention.** `SECURITY_PRIVACY.md` §7 leaves audit retention at a policy period pending legal
  approval; inventing one here would be setting policy by implementation.

## Next action

Implement and verify the cursor-query binding fix, then request a fresh independent review in a
session that did not implement the fix.

## Last updated

2026-08-04

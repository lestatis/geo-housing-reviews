# Task handoff

## Objective

Implement plan 009, chunk 1: the moderation module's foundation — build dependencies and the `V6.1`
schema for reports, cases, decisions and appeals, with migration integration coverage. No production
Java beyond the migration; nothing consumes the module yet.

## Active branch

`feat/009-moderation-chunk1-foundation`, branched from clean `main` at `cec1e39`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 1 of 8.

## Current status

completed, awaiting independent review

## Completed work

- `modules/moderation/build.gradle.kts` gained the Spring Boot BOM, `spring-boot-starter-data-jpa`
  and `assertj`. The reviews dependency (chunk 4) and web (chunk 6) are deliberately not added yet.
- `V6.1__create_moderation_tables.sql` creates `moderation.moderation_case`, `moderation.report`,
  `moderation.moderation_decision` and `moderation.appeal`.
- All cross-module references (`target_id`, `reporter_account_id`, `appellant_account_id`, decider
  and moderator ids) are opaque UUIDs with no foreign key out of the schema.
- `ModerationMigrationIntegrationTest` — 13 tests on Testcontainers Postgres.
- `docs/plans/009-moderation-module.md` written; `docs/DECISION_LOG.md` gained `P-013`.

## Remaining work

Chunks 2–8 of plan 009. Chunk 2 (domain model) is next and needs a new branch from `main`.

## Decisions made

Founder decisions, recorded as `P-013`:

- **Scope stops before right of reply.** A public representative reply is only safe once the claim to
  represent a property is verified, and MVP_SCOPE lists that claim workflow as a Should-have that
  does not exist. Coupling them would block the Must-have half from merging. Plan 010.
- **Moderators are `ADMIN` accounts.** The rules that matter key off account id, which already
  exists. A `MODERATOR` role would mean an identity `V2.x` migration for a separation nobody has
  needed yet; recorded as a follow-up.

Schema decisions taken while implementing:

- **One live case per target**, via a partial unique index where `status <> 'CLOSED'`. Twenty reports
  about one review must converge on one case; otherwise a coordinated group floods the queue, which
  is the brigading MODERATION.md asks the platform to resist. `CLOSED` being the only terminal status
  means a settled case does not block a fresh one if the content is reported again later.
- **One live report per account per target**, via a partial unique index where status is
  `OPEN`/`LINKED`. Re-reporting after a terminal outcome is allowed, because a genuinely new problem
  with the same content deserves to be heard.
- **Adverse decisions must carry a user-visible explanation** (`APPROVE` and `ESCALATE` need none) —
  a CHECK, not a service convention. `public_explanation` is separate from `internal_note` so abuse
  signals never reach the user.
- **Decisions have no `updated_at` and no version column.** An appeal must be able to show what was
  decided and under which policy version, so nothing updates a decision; a changed outcome is a new
  row.
- **`original_decider_account_id` is copied onto the appeal row** so "a different reviewer decides the
  appeal" is a single-row CHECK the database enforces, rather than a rule living only in a service a
  future code path could forget to call.
- **`LEGAL_REQUEST` is a case trigger** so an owner's or developer's takedown demand travels the same
  audited workflow as any other report (MODERATION.md anti-capture).
- `target_type` is a single-value CHECK (`REVIEW`) today; widening it later is then a deliberate,
  reviewable migration rather than a silent one.

## Assumptions

- `trigger` is a reserved word in PostgreSQL, so the column is `trigger_source`.
- Report categories mirror MODERATION.md's nine listed categories exactly.

## Files changed

- `apps/api/modules/moderation/build.gradle.kts`
- new `apps/api/modules/moderation/src/main/resources/db/migration/moderation/V6.1__create_moderation_tables.sql`
- new `apps/api/app/src/test/java/com/example/geohousing/app/moderation/ModerationMigrationIntegrationTest.java`
- new `docs/plans/009-moderation-module.md`
- `docs/DECISION_LOG.md`, `docs/handoffs/current-task.md`

## Commands run

- scratch Postgres (`postgis/postgis:18-3.6`), migration applied and all 28 constraint probes run
  statement-by-statement before any test was written; container stopped afterwards
- `cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.moderation.ModerationMigrationIntegrationTest'`
- `cd apps/api && ./gradlew :modules:moderation:check`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. 13 integration tests: the migration applied; reports converge on one live
case; a closed case does not block a fresh one; a case being worked has an assignee; one account
cannot report the same content twice while a report is live but can after it is closed out; an
`OTHER` report must say what is wrong; a report past intake carries its case; an adverse decision
must explain itself while `APPROVE` need not; a decision always carries a reason code; an appeal is
heard once and cannot be decided by the original moderator; a decided appeal owes an outcome and a
timestamp; and the schema holds no foreign key out of `moderation`.

## Known failures

None observed.

## Risks and unresolved questions

- The one-live-case index is the queue's flood defence. If a target ever needs two concurrent cases
  for unrelated issues, the forward fix is a discriminator column in the index, not dropping it.
- Reason codes are a free `VARCHAR(64)` at the schema level; the taxonomy becomes a domain enum in
  chunk 2. The column is deliberately not a CHECK so the vocabulary can grow without a migration per
  reason code.

## Human actions required

None.

## Recommended next action

Independent review of the chunk-1 branch in a fresh session, then merge. When requested, start plan
009 chunk 2 (domain model) from `main`.

## Last updated

2026-07-28

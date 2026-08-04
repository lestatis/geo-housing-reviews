# Task handoff

## Objective

Implement plan 015, chunk 1: make the audit log readable — one timeline across every module, over
HTTP.

## Active branch

`feat/015-audit-log-chunk1`, branched from clean `main` at `501d8fa`. Local only; not pushed.
Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/015-audit-log-readable.md`, chunk 1 of 2.

## Current status

completed, awaiting independent review

## Completed work

- `shared-kernel` gains its first real contents: `AuditEntry` (when, who, which action, on what, how
  it ended, why) and the `AuditTrail` port every module implements.
- Five trails — identity, properties, reviews, verification, moderation — each answering only about
  its own tables. Three entities that were write-only gained read accessors; each Spring Data
  repository gained one windowed, actor-filtered, newest-first query.
- `AuditTimelineService` merges them: each trail asked for the full limit, merged, trimmed again.
- `GET /api/admin/audit?since=&until=&actor=&limit=` behind the existing admin gate.
- Reading the timeline is itself audited (`VIEW_AUDIT`, migration `V2.9`, published through
  `identity.api.AuditReadRecorder`). An unrecorded read is not a successful one.
- Four Gherkin scenarios; `AuditSteps`. `AdminAuditEventTest` covers the factories in identity's own
  suite, where its mutation gate can see them.
- `mutationTesting.targetTests` is now configurable — see below.

## Remaining work

Plan 015 chunk 2: the timeline screen in `apps/web`.

## Decisions made

- **The port lives in `shared-kernel`, not five `api` packages.** Five separate interfaces cannot be
  collected polymorphically, so the app would need five injection points and five adapters to a
  common type — the translation layer the shared entry existed to avoid.
- **No internal note, no public explanation in the timeline.** A moderator's note lives on its case.
  The timeline says an action happened; the module owning the subject says what it contained.
- **A window is mandatory and a reversed one is refused.** Answering an impossible window with an
  empty list reads as "nothing happened", which is the one answer an audit log must never give by
  accident.
- **`AuditEntry` is a class, not a record** — half its fields are optional, and records cannot have
  accessors of a different type than their components.

## Changed files

New: `shared-kernel/.../audit/{AuditEntry,AuditTrail}.java` and `AuditEntryTest`,
`identity/api/AuditReadRecorder.java`, `identity/infrastructure/AuditReadRecorderAdapter.java`,
five `Jpa*AuditTrail.java`, `app/.../audit/{AuditQuery,AuditTimelineService,AuditController,
AuditEntryView,AuditTimelineResponse,AuditBeanConfiguration}.java`, their tests,
`acceptance/AuditSteps.java`, `identity/domain/AdminAuditEventTest.java`,
`V2.9__audit_the_audit_read.sql`, `docs/plans/015-audit-log-readable.md`.

Modified: `AdminAuditAction`, `AdminAuditEvent`, five Spring Data repositories, three audit entities
(read accessors), five `build.gradle.kts` (shared-kernel dependency),
`buildSrc/.../MutationTestingExtension.kt` and the mutation convention,
`MigrationHistorySplitIntegrationTest`, `moderate-and-administer.feature`, `docs/api/openapi.json`,
`.claude/rules/testing.md`.

## Commands and tests

```bash
cd apps/api && ./gradlew :app:test --tests '*Audit*' -PskipMutation
./scripts/check.sh          # read the EXIT= marker, not a wrapper's status
```

Three proofs, each by breaking the thing: limiting the merge to one trail fails the cross-module
scenarios; dropping the actor filter fails exactly the follow-one-administrator scenario; making
properties' trail reach `identity.domain` fails `ModuleBoundaryArchitectureTest`.

## Failures and blockers

None outstanding.

**The mutation gate had a silent hole.** `shared-kernel` scored 0% on its first run — not because
mutants survived, but because the test glob is built from the Gradle module name and this module's
package is `shared`, so no test was ever selected. PITest reports that as a score rather than as
having selected nothing. `targetTests` is now configurable and the module is at 100%. Every other
module's name matches its package, so their scores have always been real — checked, not assumed.

## Unresolved risks

- **The timeline concentrates what was scattered.** Five tables nobody could read become one screen
  answering "what has this moderator been doing" — useful for the access review `SECURITY_PRIVACY.md`
  §4 asks for, and equally a way to pressure a moderator. Admin-only and audited is what the platform
  can do about it; naming it here so a reviewer weighs it deliberately.
- **No paging.** One window, newest first, `limit` clamped to 200. Deep paging across five
  independently ordered sources needs a composite cursor and is not built.
- **Five queries per page.** Fine now; the first shape to revisit if the audit tables grow.
- **No retention.** `SECURITY_PRIVACY.md` §7 leaves audit retention at a policy period still pending
  legal approval, and inventing one here would be setting policy by implementation.

## Next action

Independent review by a fresh session that did not implement this, then plan 015 chunk 2 — the
timeline screen. Seven branches now await review; none are pushed.

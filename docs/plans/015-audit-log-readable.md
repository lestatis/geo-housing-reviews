# Plan 015 — the audit log, made readable

Status: **chunk 1 complete**; chunk 2 (the screen) outstanding

## Context

Everything plans 012–014 built records what it did, and none of it can be read back. Five separate
append-only tables now hold the history of every privileged action:

| Source | Table | Records |
| --- | --- | --- |
| identity | `admin_audit_event` | account views, role grants and removals, restrictions placed and lifted |
| properties | `property_admin_audit_event` | activate, hide, merge |
| reviews | `review_moderation_audit_event` | publish, reject, hide, restore, remove |
| verification | `verification_decision_audit_event`, `verification_evidence_access_event` | decisions, and every read of a private document |
| moderation | `moderation_decision` | the decisions themselves, which are the audit |

PRD §5.8 lists "audit log" among the admin interface's contents; plan 012 recorded it as out of
scope precisely because no read endpoint existed. `SECURITY_PRIVACY.md` §4 requires an "append-only
audit log with restricted access" and "periodic access review for moderators" — the second is not
possible without the first being readable.

The gap has a sharp edge. Any administrator can now grant `ADMIN` to anyone (plan 013), and the
audit row is the only record of how someone became privileged. Writing that row and never being able
to look at it is most of the way to not having it.

**Outcome:** an administrator can ask "what has been done, by whom, to what, and when" across every
module, through one endpoint and one screen.

## Approach

**One shared vocabulary.** Five sources already share a shape — when, who, which action, on what,
how it ended, and sometimes why. `shared-kernel` gains an `AuditEntry` record, its first real
contents. `.claude/rules/architecture.md` allows a cross-cutting abstraction with at least two
concrete use cases; there are five, and the alternative is five near-identical records plus five
mappers in the app, which makes the app a translation layer rather than a caller.

**Each module publishes a read port** in its `api` package:

```java
public interface AuditTrail {
  List<AuditEntry> recorded(Instant from, Instant until, UUID actorAccountId, int limit);
}
```

`actorAccountId` is nullable, meaning "anyone". Modules answer only about their own tables — no
cross-module SQL, which the boundary rules forbid and `ModuleBoundaryArchitectureTest` enforces.

**The app merges.** A small service asks all five, merges newest-first, and trims to `limit`.
Over-fetching `limit` from each and trimming is correct for a single page and needs no coordination
between sources.

**`GET /api/admin/audit?actor=&since=&until=&limit=`**, behind the existing `/api/admin/**` gate.

**Reading the audit is itself audited.** A new identity action `VIEW_AUDIT` (migration `V2.9`), for
the same reason account views are: this endpoint answers "who has been looking at what", and an
access review that cannot see its own reviewers is incomplete. It does create a feedback loop —
each read becomes a row a later read will show — which is honest rather than a defect.

## Deliberately not included

- **`internalNote` never enters the timeline.** A moderator's note to another moderator lives on the
  case, where it was written and where its context is. The timeline says what happened; widening
  where the note is readable is not something a merge view should quietly do.
- **No deep paging.** One page, newest-first, bounded by a time window. Cursoring across five
  independently ordered sources needs a composite cursor and is not worth it until somebody is
  actually paging through history.
- **No retention job.** `SECURITY_PRIVACY.md` §7 puts audit events at a "policy/legal period" still
  pending approval, and inventing one here would be setting policy by implementation.

## Chunks

### 1. The trail, readable over HTTP — **complete**

`shared-kernel`: `AuditEntry`. Each module: an `api.AuditTrail` port, an adapter over its own
table(s), and — where a module has two sources, as verification does — one port answering for both.
App: the merging service, `GET /api/admin/audit`, `V2.9` for `VIEW_AUDIT`, Gherkin scenarios.

Test-first for the merge (ordering, trimming, an actor filter that crosses modules) and for each
adapter's mapping. Every module gains its first `api` consumer outside itself, so
`ModuleBoundaryArchitectureTest` must be shown to fail if an adapter reaches past `api`.

### 2. The timeline screen

`apps/web`: `/audit`, filterable by actor and window, with the same view-model allowlist the other
screens use. Playwright: an administrator grants a role, then finds that grant in the timeline —
which is the whole point of the feature in one journey.

## Critical files

| What | Path |
| --- | --- |
| Shared shape (new) | `apps/api/modules/shared-kernel/src/main/java/com/example/geohousing/shared/audit/AuditEntry.java` |
| Ports (new, one per module) | e.g. `modules/identity/src/main/java/.../identity/api/AuditTrail.java` |
| Existing audit writers to read from | `identity/.../JpaAdminAuditEventRepository.java` and each module's equivalent |
| Merge + endpoint (new) | `app/src/main/java/com/example/geohousing/app/audit/` |
| Migration | `modules/identity/src/main/resources/db/migration/identity/V2.9__audit_the_audit_read.sql` |
| Boundary rule | `app/src/test/java/.../architecture/ModuleBoundaryArchitectureTest.java` |

## Verification

```bash
cd apps/api && ./gradlew :app:test --tests '*Audit*' -PskipMutation
./scripts/check.sh          # read the EXIT= marker
cd apps/web && pnpm e2e     # needs the stack; see apps/web/README.md
```

Three things shown rather than assumed:

1. **The merge really merges** — a scenario that acts in two modules (grant a role, hide a property)
   and finds both in one response, newest first.
2. **The actor filter is load-bearing** — remove it and the scenario asserting one administrator's
   actions fails.
3. **The boundary holds** — make an adapter reach past another module's `api` and watch
   `ModuleBoundaryArchitectureTest` fail.

Migrations now follow `CONTRIBUTING.md`'s Migrations section: `V2.9` is identity's next free number,
and nothing about the other modules matters.

## Risks

- **`shared-kernel` stops being empty.** That is the point at which it starts being able to become a
  dumping ground, which the boundary rules warn about. One record, no behaviour, and a reviewer
  should push back hard on the second thing that wants to live there.
- **The timeline concentrates what was previously scattered.** Five tables an administrator could not
  read become one screen that answers "what has this moderator been doing" — useful for access
  review, and equally a tool for pressuring a moderator. It is `ADMIN`-only and audited, which is
  what the platform can do about that; worth naming rather than discovering later.
- **Over-fetch-and-trim costs five queries per page.** Fine at current volume, and the shape to
  revisit first if the audit tables grow.


## What changed from the plan during implementation

**The port moved to `shared-kernel` too.** The plan had each module publishing its own
`api.AuditTrail`. Writing the merge showed why that cannot work: five separate interfaces cannot be
collected polymorphically, so the app would need five injection points and five adapters to a common
type — the translation layer the shared `AuditEntry` existed to avoid. One interface, five
implementations, and Spring collects them; a module that starts recording something new appears in
the timeline without the merging service learning its name.

**`AuditEntry` is a class, not a record.** Half its fields are genuinely optional and records cannot
have accessors of a different type than their components. It matches `UserRestriction` and
`AdminAuditEvent`, the types in the modules that populate it.

**Reading the audit needed a second published port.** `AuditReadRecorder` — identity owns the admin
audit table, and the app writing to it directly is the boundary violation the whole design avoids.

## A silent hole in the mutation gate

`shared-kernel` scored **0%** on its first run. Not because mutants survived: the convention builds
the test glob from the Gradle module name, and `shared-kernel`'s package is `shared`, so no test was
ever selected and every mutant "survived" untested. PITest reports that as a score rather than as
having selected nothing.

`mutationTesting.targetTests` is now configurable, `shared-kernel` sets it, and the score is 100%
with the threshold pinned there. Every other module's Gradle name matches its package, so their
scores have always been real — checked, not assumed. Recorded in `.claude/rules/testing.md`, because
the failure mode is invisible: a module could be added tomorrow with the same mismatch and its gate
would pass while measuring nothing.

## Verification actually performed

Three proofs, each by breaking the thing and watching exactly the right scenario fail:

1. **The merge spans modules** — limiting it to one trail fails the cross-module timeline scenarios.
2. **The actor filter reaches every module** — dropping it fails "the timeline can follow one
   administrator across modules", and nothing else.
3. **The boundary holds** — making properties' trail import `identity.domain.AccountRole` fails
   `ModuleBoundaryArchitectureTest`.

Also caught by the gate rather than by reasoning: `V2.9` broke `MigrationHistorySplitIntegrationTest`,
whose rewind removed named versions. It now removes identity's migrations above `2.6` by number, so
the next one does not break it again.

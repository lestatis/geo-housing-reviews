# Plan 013 — account roles and restrictions

Status: **chunks 1–2 complete**; chunks 3–4 outstanding

## Context

Plan 012 delivered the admin interface, and in doing so exposed why it cannot yet be operated.
Loop 5's condition is "operate without database access", but:

- **Nothing in the product grants or removes a role.** Identity exposes only
  `GET /api/admin/accounts/{accountId}`. Every admin is made with SQL — which is why both
  `apps/api/.../acceptance/TestApi.java` and `apps/web/e2e/seed.ts` write `identity.account.role`
  directly. PRD §5.8's "role-based access" is enforced and unmanageable.
- **`RESTRICT_ACCOUNT` is a moderation decision that does nothing.**
  `ReviewsModerationEffectApplier` (line ~128) says outright that it "is identity's to apply, not
  reviews'", and nothing applies it. A moderator records the decision, the author is told they have
  been restricted, and the account is untouched.
- **A restriction barely restricts.** `UserRestriction.isActiveAt` is consulted in exactly one
  place — `ProfileService`, to block a pseudonym change. A restricted account can still submit
  reviews, file reports and appeal.

The schema is already right and unused: `identity.user_restriction` (V2.3) has scope, reason,
window, moderator and appeal status; `identity.admin_audit_event` (V2.5) is append-only with an
explicit action vocabulary. `UserRestriction` has only a `reconstitute` factory and a javadoc saying
placing one "is a moderation concern (a future module)". This plan is that module's other half.

**Outcome:** an administrator can find an account, change its role, restrict it and lift the
restriction — all through the API, all audited — and a restriction stops the things it claims to.

## Non-goals

- **The very first administrator still needs SQL.** Bootstrapping privilege from nothing requires a
  seeded credential or an env-var backdoor, and adding one to unblock convenience is a poor trade.
  This plan makes it a one-time action instead of routine, and documents it.
- **No admin tiers.** Any `ADMIN` may grant `ADMIN`. `P-013` already records that a single tier is
  the current model; splitting it is an identity migration for a separation nobody has needed yet.
- **No restriction appeal flow.** `user_restriction.appeal_status` exists and stays `NONE`. The
  moderation appeal channel is keyed to a `ModerationDecision`, so appealing a restriction is a
  distinct workflow — named here so its absence is a decision.

## Chunks

Each is a branch from `main`, self-checked with `./scripts/check.sh`, independently reviewed.

### 1. Roles, changed through the API — **complete**

- `Account.changeRole(AccountRole, Clock)` — test-first. A closed account's role cannot change; the
  aggregate refuses rather than the service remembering to check. It also refuses a change to the
  role already held: every change is audited, and a no-op that still wrote a row would put a grant
  in the log that granted nothing.
- `AccountRepository` gains `save(Account, long expectedVersion)` and `countByRole(AccountRole)`,
  following `PublicProfileRepository.save`'s optimistic-locking shape.
- `AccountRoleService.changeRole(adminId, targetId, newRole, expectedVersion)`, every outcome
  audited including refusals.

  **The plan's "an admin cannot change their own role" rule was dropped during implementation**, and
  the acceptance scenario asserting it is what exposed why. To demote somebody you must be an
  administrator, so the target is never the last one — which made the last-administrator guard
  unreachable. Self-promotion needs no rule either: only an administrator can call this, so raising
  your own role is a role you already hold and the aggregate refuses it. What remains is the rule
  that actually protects the platform: **the last administrator cannot step down**, and stepping
  down while somebody else holds the role is allowed, which is a legitimate thing to want.

- `AdminAccountView` gained `version` — the endpoint demands the version the administrator saw, and
  without exposing it no caller could supply one.
- `V2.7__widen_admin_audit_vocabulary.sql`: add `GRANT_ADMIN`, `REVOKE_ADMIN`, `RESTRICT_ACCOUNT`,
  `LIFT_RESTRICTION` to the `action` CHECK, and `APPLIED`/`REFUSED` to `outcome`. Append-only, so a
  new migration is how the vocabulary grows — the existing comment in V2.5 says exactly this.
- `PATCH /api/admin/accounts/{accountId}/role` taking `{role, version}`.
- Gherkin scenarios in `moderate-and-administer.feature`, including every refusal.
- The SQL in `TestApi.grantAdministrator` and `apps/web/e2e/seed.ts` now runs only for the *first*
  administrator; every later grant goes through the endpoint, so it is exercised by every scenario
  needing two administrators rather than only by the ones written to test it.
- One further direct write remains, and is new: `TestApi.leaveOnlyAdministrator`. Scenarios share a
  database and each leaves its administrators behind, so "and nobody else is an administrator" is a
  statement about the whole population that no endpoint expresses — without it the
  last-administrator rule can never become true in an acceptance test.

### 2. Finding an account, and restricting it — **complete**

- `GET /api/admin/accounts?pseudonym=` — an admin looking at a reported review knows the pseudonym
  and nothing else. Audited as `VIEW_ACCOUNT`, same as the by-id lookup.
- `UserRestriction.place(...)` factory with the invariants the schema already asserts.
- `UserRestrictionRepository` gained `create`, `findById`, `findAllFor` and `save`. Lifting rewrites
  the row with a closed window rather than deleting it, because a lifted restriction is history: an
  appeal, or a later moderator judging a pattern, needs to see that it happened and when it stopped.
  The history endpoint therefore returns lifted restrictions too, each marked `active: false`.
- `V2.8__audit_account_restrictions.sql` adds `RESTRICT_ACCOUNT` and `LIFT_RESTRICTION`. A lift is
  its own action rather than a restrict with a different outcome — ending somebody else's
  restriction early is a distinct decision, and a log that conflated them could not answer "who let
  this account back in?".
- `requireText` now trims. The schema's CHECK is `length(trim(reason)) > 0`, so the database already
  treated surrounding space as absent; storing it anyway only meant the reason shown to a restricted
  account carried whitespace nobody typed.
- `AccountRestrictionService.restrict(...)` / `.lift(...)`, audited, refusing a restriction on an
  account that already has an active one of the same scope.
- `POST /api/admin/accounts/{accountId}/restrictions`, `POST .../restrictions/{id}/lift`,
  `GET .../restrictions`.

### 3. A restriction that restricts

- New `identity.api` port (the package exists and is empty):
  `AccountStanding { boolean isRestricted(UUID accountId); }` plus a published
  `AccountRestrictedException`. Identity learns nothing about its callers.
- `reviews` and `moderation` gain `implementation(project(":modules:identity"))`, reaching only
  `identity.api` — `ModuleBoundaryArchitectureTest` enforces that, and must be *shown* to fail if
  the rule is bypassed, as it was for moderation→reviews in plan 009.
- Refuse from a restricted account: submitting or editing a review, filing a report, filing an
  appeal. Reading stays open — a restriction is not an erasure.
- **`RESTRICT_ACCOUNT` finally applies.** Moderation's effect applier routes account-scoped actions
  to identity and content-scoped ones to reviews, replacing the no-op arm and its comment.

### 4. The account screen

`apps/web`: look up by pseudonym, see role, status and restriction history, change role, place and
lift a restriction. Same shape as chunks 2–3 of plan 012 — server actions bound not wrapped, a
view-model allowlist, Playwright journeys including each refusal.

## Critical files

| Area | Path |
|---|---|
| Aggregate | `apps/api/modules/identity/.../domain/Account.java`, `UserRestriction.java` |
| Audit vocabulary | `.../domain/AdminAuditAction.java`, `AdminAuditOutcome.java`, `AdminAuditEvent.java` |
| Ports | `.../application/AccountRepository.java`, `UserRestrictionRepository.java` |
| Services | `.../application/AdminAccountService.java` (precedent for audit-on-every-call) |
| Web | `.../infrastructure/web/AdminAccountController.java`, `IdentityExceptionHandler.java` |
| Migration | `apps/api/modules/identity/src/main/resources/db/migration/identity/V2.7__*.sql` |
| Cross-module precedent | `apps/api/modules/reviews/src/main/java/.../reviews/api/` |
| Effect routing | `apps/api/modules/moderation/.../infrastructure/reviews/ReviewsModerationEffectApplier.java` |
| Test seams | `apps/api/app/src/test/java/.../acceptance/TestApi.java`, `apps/web/e2e/seed.ts` |

## Verification

```bash
cd apps/api && ./gradlew :modules:identity:test -PskipMutation      # fast loop
./scripts/check.sh                                                   # read the EXIT= marker

# end to end, per apps/web/README.md
cd apps/web && pnpm e2e
```

Beyond the gate, three things must be *shown*, not assumed — the pattern this repo has used
throughout:

1. **The last-admin and self-change refusals are load-bearing** — remove each guard, watch exactly
   the scenario that asserts it fail.
2. **The module boundary holds** — make `reviews` touch an identity internal and watch
   `ModuleBoundaryArchitectureTest` fail.
3. **A restriction actually blocks** — the Gherkin scenario for "a restricted resident cannot submit
   a review" must fail if the check is removed.

Identity's mutation threshold is in `apps/api/modules/identity/build.gradle.kts` (currently 80);
ratchet it if this work leaves the score above it.

## Risks

- **Privilege escalation is now an API call.** Any admin can make anyone an admin. That is the
  single-tier model working as designed, but it makes the audit log the only record of how someone
  became privileged — so the audit write must be on the normal path and must fail the request if it
  fails, as `AdminAccountService.viewAccount` already does.
- **Adding an identity dependency to `reviews` and `moderation`** is the first time either has
  depended on identity. It is one-way and acyclic (identity depends on no module), but it is a new
  module boundary and needs `docs/ARCHITECTURE.md` updated.
- **Restriction enforcement changes existing endpoint behaviour** — submissions that used to succeed
  will start returning 403. Every affected path needs a negative-path test, and the reason must be
  specific enough for the account to understand it.

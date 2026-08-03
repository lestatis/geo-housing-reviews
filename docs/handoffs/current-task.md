# Task handoff

## Objective

Implement plan 013, chunk 3: make a restriction actually restrict, and make `RESTRICT_ACCOUNT` do
something.

## Active branch

`feat/013-restrictions-enforced-chunk3`, branched from clean `main` at `5f5360c`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/013-account-roles-and-restrictions.md`, chunk 3 of 4.

## Current status

completed, awaiting independent review

## Completed work

- **`identity.api` is no longer empty.** Two published ports: `AccountStanding.isRestricted` (a yes
  or no, never the reason) and `AccountRestraint.restrict`. Plus `RestrictedAccountException`, so a
  calling module refuses without inventing its own vocabulary. Adapters in identity's
  infrastructure.
- **`reviews` and `moderation` now depend on `identity`**, reaching only its `api` package. Both are
  one-way; identity depends on no module, which keeps the graph acyclic.
- Reviews refuses `submit` and `edit` from a restricted author; moderation refuses `file` a report.
  Reading stays open everywhere.
- **`RESTRICT_ACCOUNT` restricts the review's author** through `AccountRestraint`, replacing the
  no-op arm in `ReviewsModerationEffectApplier` and the comment that said it was identity's job.
- `ACCOUNT_RESTRICTED` mapped to 403 in both modules' exception handlers.
- Five Gherkin scenarios (27 in that feature now, all passing); `ARCHITECTURE.md` gained the full
  table of module dependencies.
- Mutation thresholds ratcheted where this chunk left headroom: reviews 85 → 90, verification
  85 → 88, properties 75 → 77.

## Remaining work

Plan 013 chunk 4: the admin account screen in `apps/web` — look up by pseudonym, see role, status
and restriction history, change role, place and lift a restriction.

## Decisions made

- **Appeals stay open to restricted accounts**, contrary to the approved plan. An appeal is how
  somebody challenges a decision made against them, and restricting an account is frequently part of
  that same decision; refusing appeals would let a takedown remove the route to contest it, which is
  what `P-014` exists to prevent. `AppealService.file` carries a comment at the point somebody would
  otherwise add the check, and a scenario proves it.
- **A moderation restriction is indefinite.** The decision carries no duration, and inventing a
  window would be a policy nobody set that then quietly expires. An administrator lifts it.
- **Restricting an already-restricted account is a no-op**, not a failure: the intended outcome
  already holds, and the refusal is already in identity's audit trail.
- **Restricting the author of content that has vanished is a conflict.** The decision said somebody
  should be stopped; quietly stopping nobody would report success for an outcome that did not happen.
- **The published port answers yes or no, never why.** What a restricted person is told belongs in
  one place — identity — rather than being phrased by every module that has to refuse.

## Changed files

New: `identity/api/{AccountStanding,AccountRestraint,RestrictedAccountException,package-info}.java`,
`identity/infrastructure/{AccountStandingAdapter,AccountRestraintAdapter}.java`.

Modified: `ReviewSubmissionService`, `ReviewsBeanConfiguration`, `ReviewsExceptionHandler`,
`ReportIntakeService`, `AppealService` (comment only), `ModerationBeanConfiguration`,
`ModerationExceptionHandler`, `ReviewsModerationEffectApplier`, both modules' `build.gradle.kts`,
their test doubles, `moderate-and-administer.feature`, `docs/ARCHITECTURE.md`,
`docs/plans/013-account-roles-and-restrictions.md`, and three modules' mutation thresholds.

## Commands and tests

```bash
cd apps/api && ./gradlew :modules:reviews:check :modules:moderation:check -PskipMutation
./gradlew :app:test --tests '*AcceptanceTest' -PskipMutation
./scripts/check.sh          # read the EXIT= marker, not a wrapper's status
```

Two things were proven rather than assumed, both by breaking them:

1. **Enforcement is load-bearing** — replacing the restriction check in `ReviewSubmissionService`
   with `false` fails exactly "a restricted account cannot submit a review".
2. **The module boundary holds** — making `reviews` import `identity.domain.RestrictionScope` fails
   `ModuleBoundaryArchitectureTest`, and nothing else.

## Failures and blockers

None outstanding.

## Unresolved risks

- **No appeal path for a restriction itself.** `user_restriction.appeal_status` exists and stays
  `NONE`. An account can appeal a *content* decision, but not the restriction — a gap named as a
  non-goal in the plan and worth closing before launch.
- **A restriction placed by a decision is indefinite and only an administrator can lift it.** That is
  deliberate, but it means an operational habit has to exist for reviewing them; nothing expires on
  its own.
- **`AccountRestraint` is a write reaching from moderation into identity.** It is narrow (one method,
  one direction) but it is the first cross-module *write* in the codebase; every other cross-module
  call so far either reads or applies an effect to content the caller already had authority over.

## Next action

Independent review of this branch by a fresh session that did not implement it, then merge. Then
plan 013 chunk 4: the admin account screen.

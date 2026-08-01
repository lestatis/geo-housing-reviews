# Task handoff

## Objective

Implement plan 013, chunk 2: find the account behind a pseudonym, and place and lift the
restrictions MODERATION.md says a moderator may impose.

## Active branch

`feat/013-account-restrictions-chunk2`, branched from clean `main` at `4b69fe6`. Local only; not
pushed.
Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/013-account-roles-and-restrictions.md`, chunk 2 of 4.

## Current status

completed, awaiting independent review

## Completed work

- `UserRestriction.place(...)` and `liftedAt(...)` — test-first. A restriction is always attributed
  and always explained; lifting closes the window rather than removing the row.
- `UserRestrictionRepository` gained `create`, `findById`, `findAllFor`, `save`; JPA adapter
  implements all four.
- `AccountRestrictionService.restrict/lift/history`, auditing every outcome including refusals.
  Refuses a second active restriction in the same scope, and refuses lifting one that has ended.
- `V2.8__audit_account_restrictions.sql` adds `RESTRICT_ACCOUNT` and `LIFT_RESTRICTION`.
- `GET /api/admin/accounts?pseudonym=`, audited as `VIEW_ACCOUNT`;
  `POST /api/admin/accounts/{id}/restrictions`, `.../restrictions/{id}/lift`, and
  `GET .../restrictions`.
- `requireText` now trims — the schema's CHECK already ignored surrounding space.
- Six Gherkin scenarios; `RestrictionSteps`. Identity's mutation threshold ratcheted 85 → 90.

### From chunk 1 (unchanged, already merged)

- `Account.changeRole(AccountRole, Clock)` — refuses a closed account, and refuses a change to the
  role already held (a no-op that still wrote an audit row would put a grant in the log that
  granted nothing).
- `AccountRepository` gained `save(Account, long expectedVersion)` and `countByRole(AccountRole)`;
  the JPA adapter implements both, counting only `ACTIVE` accounts.
- `AccountRoleService`, auditing every outcome including refusals.
- `AdminAuditAction` gained `GRANT_ADMIN`/`REVOKE_ADMIN`; `AdminAuditOutcome` gained
  `APPLIED`/`REFUSED`; `V2.7__widen_admin_audit_vocabulary.sql` widens both CHECKs.
- `PATCH /api/admin/accounts/{accountId}/role` taking `{role, version}`; `LAST_ADMINISTRATOR`
  mapped to 409. `AdminAccountView` gained `version`.
- Six existing test doubles gained the two new port methods, throwing rather than returning a
  plausible default — a silent `0` from `countByRole` would make the last-administrator check pass
  wrongly.
- Six Gherkin scenarios; `RoleSteps`. `docs/api/openapi.json` regenerated.
- `CONTRIBUTING.md` gained the orphaned-volume half of the Testcontainers recovery, found the hard
  way when the disk filled mid-gate.

## Remaining work

Plan 013 chunk 3 — restriction enforcement across reviews and moderation, including making
`RESTRICT_ACCOUNT` actually apply — and chunk 4, the admin account screen.

## Decisions made

- **The plan's "an administrator cannot change their own role" rule was dropped.** Writing the
  acceptance scenario for the last-administrator rule is what exposed why: to demote somebody you
  must be an administrator, so the target is never the last one — the guard was unreachable.
  Self-promotion needs no rule either, because only an administrator can call this and raising your
  own role means a role you already hold. What remains is the rule that protects the platform: the
  last administrator cannot step down. Stepping down while somebody else holds the role is allowed.
- **`AdminAccountView` exposes `version`.** The endpoint demands the version the administrator saw;
  without exposing it, no caller could supply one.
- **The first administrator is still made with SQL**, in both test suites. Bootstrapping privilege
  from nothing needs a seeded credential or a backdoor; this plan makes the write a one-time action
  instead of routine. Every later grant goes through the endpoint.
- **`TestApi.leaveOnlyAdministrator` is a new direct write, on purpose.** Scenarios share a database
  and each leaves its administrators behind, so "and nobody else is an administrator" is a statement
  about the whole population that no endpoint expresses.

## Changed files

New: `identity/application/{AccountRoleService,LastAdministratorException}.java`,
`identity/infrastructure/web/ChangeRoleRequest.java`,
`identity/src/main/resources/db/migration/identity/V2.7__widen_admin_audit_vocabulary.sql`,
`identity/src/test/.../application/AccountRoleServiceTest.java`,
`app/src/test/.../acceptance/RoleSteps.java`,
`docs/plans/013-account-roles-and-restrictions.md`.

Modified: `Account.java`, `AdminAuditAction.java`, `AdminAuditOutcome.java`, `AdminAuditEvent.java`,
`AccountRepository.java`, `JpaIdentityPersistenceAdapter.java`, `SpringDataAccountRepository.java`,
`AccountJpaEntity.java`, `AdminAccountController.java`, `AdminAccountView.java`,
`IdentityExceptionHandler.java`, `IdentityBeanConfiguration.java`, six identity test doubles,
`AccountTest.java`, `TestApi.java`, `moderate-and-administer.feature`, `docs/api/openapi.json`,
`apps/web/e2e/seed.ts`.

## Commands and tests

```bash
cd apps/api && ./gradlew :modules:identity:check -PskipMutation
./gradlew :app:test --tests '*AcceptanceTest' -PskipMutation
./scripts/check.sh          # read the EXIT= marker, not a wrapper's status
```

Two guards were proven load-bearing by replacing each condition with `false` and watching exactly
the scenario that asserts it fail: the last-administrator rule (chunk 1) and the
already-restricted rule (this chunk).

## Failures and blockers

None outstanding. The first gate run failed with 72 `initializationError`s and no test failures:
the disk was 100% full (30 MB free of 199 GB) and no container could start. The cause was ~1176
orphaned *anonymous volumes* holding 125 GB — one per unreaped Testcontainers PostgreSQL. Stopping
or killing the containers reclaims none of it; the volumes have to be pruned separately, and the
containers removed first so their volume references are released. `CONTRIBUTING.md` documented only
the container half of this, and now documents both.

## Unresolved risks

- **Privilege escalation is now an API call.** Any administrator can make anyone an administrator.
  That is the single-tier model of `P-013` working as designed, and the audit log is the only
  record of how someone became privileged — so the audit write is on the normal path and a failure
  to record fails the request.
- **`RESTRICT_ACCOUNT` still applies no effect**, and a restriction still only blocks a pseudonym
  change. A moderator can now place one and it stops almost nothing — chunk 3 is what makes it mean
  something, and until then the endpoint promises more than it delivers.
- **No appeal path for a restriction.** `user_restriction.appeal_status` exists and stays `NONE`;
  the moderation appeal channel is keyed to a `ModerationDecision`. Named as a non-goal in the plan.
- Identity's mutation threshold was ratcheted 80 → 85 in chunk 1 and 85 → 90 here (154/172).

## Next action

Independent review of this branch by a fresh session that did not implement it, then merge. Then
plan 013 chunk 2: account lookup by pseudonym, and restrictions placed and lifted.

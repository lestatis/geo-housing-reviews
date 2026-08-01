# Task handoff

## Objective

Implement plan 013, chunk 1: grant and remove administrative access through the API, audited, so
the platform can be operated without editing its database.

## Active branch

`feat/013-account-roles-chunk1`, branched from clean `main` at `3403e56`. Local only; not pushed.
Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/013-account-roles-and-restrictions.md`, chunk 1 of 4.

## Current status

completed, awaiting independent review

## Completed work

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

Plan 013 chunks 2–4: account lookup by pseudonym and restrictions placed/lifted; restriction
enforcement across reviews and moderation (including making `RESTRICT_ACCOUNT` actually apply); the
admin account screen.

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

The last-administrator guard was proven load-bearing: replacing its condition with `false` fails
exactly the scenario that asserts it, and nothing else.

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
- **`RESTRICT_ACCOUNT` still applies no effect**, and restrictions still only block a pseudonym
  change. Both are chunk 3.
- Identity's mutation threshold was ratcheted 80 → 85, the score this chunk leaves (122/144).

## Next action

Independent review of this branch by a fresh session that did not implement it, then merge. Then
plan 013 chunk 2: account lookup by pseudonym, and restrictions placed and lifted.

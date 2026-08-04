# Task handoff

## Objective

Implement plan 014: give each module its own Flyway instance and history table, so a module's
migrations are ordered only against its own.

## Active branch

`feat/014-per-module-flyway`, branched from clean `main` at `ea8f94e`. Local only; not pushed.
Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/014-per-module-flyway.md` — the plan is **complete**.

## Current status

completed, awaiting independent review

## Completed work

- `V1__init.sql` moved to `db/migration/root/`, so each instance has a location of its own.
- `ModuleMigrationConfiguration` defines six Flyway instances — root plus one per module — each
  writing its history into its own schema. `spring.flyway.enabled: false`; JPA is held back by an
  `EntityManagerFactoryDependsOnPostProcessor`, the mechanism Spring Boot's own auto-configuration
  uses.
- New root migration `V1.1` installs `pg_trgm` in `public`.
- The five per-module migration tests now assert against their own module's history table.
- New `MigrationHistorySplitIntegrationTest` covers the upgrade from a real shared-history database.
- `CONTRIBUTING.md` gained a **Migrations** section — the version-range convention had never been
  written down anywhere, which is part of how this went unnoticed. `ARCHITECTURE.md`'s boundary rules
  now say a module owns its migration *history* too.

## Remaining work

None for plan 014.

## Decisions made

- **The baseline is read, not hardcoded.** The plan pinned each module at the version that existed
  before the split. Two prior states exist — a database that stopped at `6.1` and never got
  identity's `2.7`, and one created after that chunk shipped which has it — and a fixed baseline is
  wrong for one of them. Wrong in the dangerous direction, too: replaying `V2.7` narrows a CHECK that
  `V2.8` widens, and fails against audit rows only the later vocabulary permits.
- **Extensions belong to the root range.** A module migrating inside its own schema installs an
  extension where the runtime cannot see it, and nothing errors — catalogue search simply stops
  matching. `pg_trgm` moved from `V3.4` to `V1.1`; the old statement stays and is now a no-op.
- **The root instance got its own history table.** It shared `flyway_schema_history` with the old
  arrangement, so its own new migration sorted below the `6.1` recorded there and was refused.

## Changed files

New: `app/src/main/java/.../config/ModuleMigrationConfiguration.java`,
`app/src/main/resources/db/migration/root/V1.1__install_trigram_extension.sql`,
`app/src/test/java/.../MigrationHistorySplitIntegrationTest.java`,
`docs/plans/014-per-module-flyway.md`.

Moved: `db/migration/V1__init.sql` → `db/migration/root/V1__init.sql`.

Modified: `application.yml`, the five `*MigrationIntegrationTest` classes,
`GeoHousingApplicationIntegrationTest`, `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`.

## Commands and tests

```bash
cd apps/api && ./gradlew :app:test --tests '*MigrationIntegrationTest' \
  --tests '*MigrationHistorySplitIntegrationTest' -PskipMutation
./scripts/check.sh          # read the EXIT= marker, not a wrapper's status
```

Verified three ways: a fresh database (the per-module tests), a reconstructed pre-`2.7` database (the
new test), and by hand against the local dev database in its real state, where identity baselines at
`2.8`, re-runs nothing, and root applies `V1.1`.

## Failures and blockers

None outstanding.

Two problems were found by running rather than reasoning, and both would have shipped:
the `pg_trgm` relocation (silent — search stops matching, no error) and the root instance sharing
the old history. The second was caught only by starting against the real dev database; no test had
covered it.

## Unresolved risks

- **`baselineOnMigrate` still assumes a module's schema is never partway through its range.** True
  for anything the old shared history produced, because it was all-or-nothing per migration. A
  database left half-migrated by a *failed* run is a different matter — I created exactly that state
  on this machine during implementation, and had to clear the partial history tables by hand before
  the transition would run correctly. Worth a runbook note before the first real deployment.
- **Six migration runs at boot** rather than one. Not measured; the gate did not visibly slow, but
  nobody timed it.
- **`clean` is now per-module**, so anyone reaching for it gets a narrower blast radius than they may
  expect.

## Next action

Independent review by a fresh session that did not implement this. Six branches now await review;
none are pushed.

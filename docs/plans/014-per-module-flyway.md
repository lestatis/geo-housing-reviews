# Plan 014 — a Flyway instance per module schema

Status: **complete**

## Context

Plan 013 chunk 4 could not start the API against a database that already had migrations applied:

```text
Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 2.7.
```

Modules number their migrations by a per-module prefix — root=1, identity=2, properties=3,
reviews=4, verification=5, moderation=6 — a convention carried in the plan documents for 004, 005
and 009 and never written down as a rule. All of them land in **one** `flyway_schema_history` in
`public`, ordered globally. So every new identity migration is numbered *below* migrations the other
modules already applied, and Flyway's default `outOfOrder=false` refuses it.

Every gate has passed because Testcontainers start an empty database, where ordering is trivially
satisfied. Nothing is broken today. **The first deployment that upgrades rather than creates one
will fail to start**, and so will any developer whose local database predates the change.

Founder decision (2026-08-03): give each module its own Flyway instance and its own history table,
so a module's migrations are ordered only against its own. That matches what the modules already
are — `ARCHITECTURE.md` says each owns its tables and migrations namespace — and removes the class
of problem rather than suppressing one instance of it.

**Outcome:** adding a migration to any module is safe regardless of what the other modules have
already applied, on a fresh database and on an existing one.

## Why this is safe here

Checked before planning: **no module's migrations reference another module's tables.** Every
`REFERENCES` in every module stays inside that module's own schema, so the per-module instances have
no ordering constraints between them. The one real dependency is `V1__init.sql`
(`CREATE EXTENSION postgis`), which properties' `V3.2` needs — handled by making every module
instance depend on the root one.

## Approach

**Layout.** Move `app/src/main/resources/db/migration/V1__init.sql` to `db/migration/root/`, so each
instance gets a location that does not overlap another (Flyway locations are recursive).

**Six instances**, defined explicitly in the app module with `spring.flyway.enabled: false`:

| Instance | Location | Schema / history |
| --- | --- | --- |
| root | `db/migration/root` | `public.flyway_schema_history` |
| identity | `db/migration/identity` | `identity.flyway_schema_history` |
| properties, reviews, verification, moderation | likewise | in their own schema |

Each module instance is `@DependsOn` the root one. JPA must not validate before they run, so the
configuration includes an `EntityManagerFactoryDependsOnPostProcessor` naming them — the same
mechanism Spring Boot's own `FlywayAutoConfiguration` uses.

**The transition for an existing database.** Each module instance sets `baselineOnMigrate = true`
with a `baselineVersion` fixed at the last version that existed before this change (identity 2.6,
properties 3.5, reviews 4.5, verification 5.2, moderation 6.1). Flyway baselines only when a schema
is non-empty and has no history table — exactly the existing-database case — and then applies
anything above the baseline, which is how identity's `2.7` and `2.8` finally land. A fresh database
has no such schema, so it migrates from scratch and never baselines.

Those `baselineVersion` values are historical constants, not something to maintain: once a history
table exists the baseline is never consulted again. That has to be said in a comment, or the next
person will dutifully bump them.

## Critical files

| What | Path |
| --- | --- |
| Root migration to move | `apps/api/app/src/main/resources/db/migration/V1__init.sql` |
| Flyway config (new) | `apps/api/app/src/main/java/com/example/geohousing/app/config/ModuleMigrationConfiguration.java` |
| Disable auto-config | `apps/api/app/src/main/resources/application.yml` |
| History assertions to requalify | the five `*MigrationIntegrationTest` classes and `GeoHousingApplicationIntegrationTest` |

## Verification

```bash
cd apps/api && ./gradlew :app:test --tests '*MigrationIntegrationTest' -PskipMutation
./scripts/check.sh          # read the EXIT= marker
```

Three things must be shown, not assumed:

1. **A fresh database still works** — the existing migration tests, with their assertions requalified
   to each module's own history table. That requalification is itself the evidence the histories
   moved.
2. **An existing database upgrades.** A new test that migrates a container the *old* way first — one
   Flyway instance over all locations, one `public.flyway_schema_history` — then starts the
   application against it and asserts identity's `2.7` and `2.8` applied and every module's history
   table exists. This is the case that is broken today, so the test must fail without the change.
   `.claude/rules/testing.md` asks migration tests to start from a realistic previous state; this is
   that state.
3. **The ordering problem is genuinely gone** — add a throwaway `V2.9` to identity and confirm it
   applies to a database already carrying `6.1`, which is precisely what fails now.

Then, by hand, against the local dev database that already has the old single history:

```bash
docker compose -f infra/docker/docker-compose.yml up -d postgres
cd apps/api && OIDC_JWK_SET_URI=http://localhost:8081/default/jwks \
  IDENTITY_AUTH_SUBJECT_PEPPER=local-dev-only-pepper ./gradlew :app:bootRun
```

It currently fails on `2.7`; it must start.

## Documentation

The numbering convention is written down nowhere, which is part of how this happened. Add it to
`CONTRIBUTING.md` with the rule it now carries: **a module's migrations are ordered only against its
own**, so a new one takes the next free number in that module's range and nothing else has to be
considered. Update `docs/ARCHITECTURE.md`'s boundary rules, which already say each module owns its
migrations namespace but not that each owns its history.

## Risks

- **`baselineOnMigrate` is a loaded gun aimed at an empty schema.** If a module's schema existed but
  were only partly migrated, baselining would mark unapplied migrations as done. That cannot happen
  here — the old single history was all-or-nothing per migration — but the comment on those values
  needs to say why, because the reasoning is not visible from the code.
- **Six Flyway instances start six migration runs at boot.** Slower than one, and every integration
  test pays it. Worth measuring rather than assuming it is negligible.
- **`clean` becomes per-module.** Nothing in this repository calls it, but anyone reaching for it in
  future gets a narrower blast radius than they may expect.


## What changed from the plan during implementation

Three things the plan did not foresee, all found by running it rather than reasoning about it.

**`pg_trgm` would have moved schema, silently.** A module now migrates inside its own schema, so
properties' `V3.4` — `CREATE EXTENSION IF NOT EXISTS pg_trgm` — installed the extension into
`properties`, where the runtime search path cannot see it. Five catalogue-search scenarios failed
with a grammar error rather than anything mentioning extensions. Fixed by a new root migration
`V1.1` that installs it in `public` first, making `V3.4` a no-op instead of a relocation.

**The root instance had the same disease as the modules.** It kept writing to the old
`flyway_schema_history`, which on an existing database still holds every module's rows — so the new
`V1.1` sorted below the recorded `6.1` and was refused. It now has its own
`flyway_schema_history_root`. This was caught by starting against the real dev database, not by any
test.

**A fixed `baselineVersion` was wrong.** The plan pinned each module's baseline at the version that
existed before the split. Two prior states exist: a database that stopped at `6.1` and never
received identity's `2.7`, and one created after that chunk shipped which has it. Baselining the
second at `2.6` replays `V2.7`, which *narrows* a CHECK constraint that `V2.8` widens — and fails
outright against audit rows only the later vocabulary permits. The baseline is now read from the old
shared history per module, which is right for both.

That last one is the reason to be wary of this kind of transition: replaying a migration is not
generally safe, and the pair here is a concrete counterexample sitting in the repository.

## Verification actually performed

- Fresh database: the five per-module migration tests, requalified to each module's own history.
- Existing database: `MigrationHistorySplitIntegrationTest` builds the true pre-`2.7` state — migrate
  everything the old way, then remove `2.7`/`2.8` and restore the CHECK `V2.5` left — and asserts the
  application starts, each module gains a history, and `2.7`/`2.8` finally apply.
- By hand against the local dev database in its real post-chunk-2 state: baselines identity at `2.8`
  (read, not assumed), re-runs nothing, applies root `V1.1`, and starts.

The plan also asked for a throwaway `V2.9` to prove the ordering problem is gone. That was not done
separately: `V1.1` is exactly such a migration — numbered below every module's applied versions,
applied successfully to a database carrying `6.1` — so the property is demonstrated by the change
itself rather than by a temporary file.

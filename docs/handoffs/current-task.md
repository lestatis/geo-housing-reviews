# Task handoff

## Objective

Add generated OpenAPI documentation and Swagger UI to the Spring Boot API while preserving JWT
protection for all business endpoints.

## Active branch

`feat/swagger-openapi` (Codex left the work uncommitted on `main`; Claude Code moved it to a branch)

## Related issue or plan

`docs/plans/2026-07-19-swagger-openapi.md`

## Current status

complete_pending_review — implemented by Codex, finished and verified by Claude Code (distinct
authors). Ready for review and merge.

## Completed work

- (Codex) Added the Springdoc 3.0.3 WebMVC UI starter, `OpenApiConfiguration` (metadata + global
  HTTP bearer JWT scheme), public documentation-route authorization in `SecurityConfiguration`,
  `SwaggerEndpointIntegrationTest`, and README/API_GUIDELINES updates. Left the checks unrun and the
  work uncommitted on `main`.
- (Claude Code takeover, 2026-07-20) Ran the deferred checks and drove the running app. Springdoc
  3.0.3 resolves and works on Boot 4.1; `/v3/api-docs` (JSON) and `/swagger-ui/index.html` are
  public, `/api/me` stays 401, and the generated document lists the real identity operations
  (`/api/me`, `/api/me/export`, `/api/me/profile`, `/api/admin/accounts/{accountId}`).
- **Fixed a gap:** `/v3/api-docs.yaml` was returning 401 — API_GUIDELINES advertised it as public,
  but the `/v3/api-docs/**` matcher does not cover the `.yaml` sibling path. Added
  `/v3/api-docs.yaml` to the permitted matchers plus a regression test. Moved everything to
  `feat/swagger-openapi`.

## Remaining work

None. Independent review and merge (Codex implemented; a fresh reviewer should look at the security
matcher change).

## Decisions made

- Swagger UI and OpenAPI routes are public documentation only; `/api/**` and actuator policy
  remain unchanged.
- The generated contract specifies HTTP bearer JWT authentication globally.

## Assumptions

- The requested "Swagger" means interactive Swagger UI plus an OpenAPI 3 document, not a static
  hand-maintained file.

## Files changed

- `apps/api/gradle/libs.versions.toml`
- `apps/api/app/build.gradle.kts`
- `apps/api/app/src/main/java/com/example/geohousing/app/config/OpenApiConfiguration.java`
- `apps/api/app/src/main/java/com/example/geohousing/app/config/SecurityConfiguration.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/SwaggerEndpointIntegrationTest.java`
- `apps/api/README.md`
- `docs/API_GUIDELINES.md`
- `docs/plans/2026-07-19-swagger-openapi.md`
- `docs/handoffs/current-task.md`
- `docs/handoffs/2026-07-19-swagger-openapi.md`

## Commands run

- Read-only repository inspection and Springdoc official documentation lookup.

## Tests and verification

Run on `feat/swagger-openapi`, 2026-07-20:

- `./gradlew :app:test --tests '*SwaggerEndpointIntegrationTest*'` — passed, 4/4: OpenAPI JSON public
  with bearer scheme, Swagger UI public, **YAML doc public (new regression guard)**, `/api/me` still 401.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).
- Live smoke on the running app (dev Postgres): `/v3/api-docs` 200, `/v3/api-docs.yaml` 200 (was 401
  before the fix), `/swagger-ui/index.html` 200, `/api/me` 401. No actuator route beyond `/health`
  is public.

## Known failures

None.

## Risks and unresolved questions

- Public documentation exposes endpoint shapes by design; verified it does not expose an actuator
  route or bypass `/api/**` JWT authorization.
- The generated document currently lists only identity operations — `properties` has no controller
  yet (its module is at chunk 1, schema only), so nothing to document there until its endpoints land.

## Human actions required

Review and merge `feat/swagger-openapi`. The security-matcher change should get a fresh independent
look. The branch is local and not pushed; there is no credential path to push from this environment.

## Recommended next action

Independent review of `feat/swagger-openapi`, then merge to `main`. Afterwards, the paused work is
properties chunk 2 (domain model) on top of the merged chunk 1.

## Last updated

2026-07-20

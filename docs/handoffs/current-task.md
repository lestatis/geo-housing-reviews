# Task handoff

## Objective

Plan 023-B — mobile discovery. An anonymous user searches for a building in the mobile app and gets
results they can tell apart by address and type. Delivered as 023-B1 (backend contract change C1:
address summary and `type` on search hits) and then 023-B2 (mobile search and results screens).

## Active branch

`feat/023-b-mobile-discovery`, created from `main` at `71d8878` on 2026-11-15. Not pushed.

## Related issue or plan

`docs/plans/023-mobile-discovery.md`, section "023-B — discovery" (engineering design, chunks
023-B1 and 023-B2, `ai-l1` blocks). 023-A is merged (#2); 023-C is not started.

## Current status

in_progress — 023-B1 L1-green (4/4) and reviewed `READY_TO_MERGE` 2026-11-15
(`.ai/runs/implementation-loop.PHkk11`); uncommitted. 023-B2 dispatched next.

## Completed work

- Re-verified plan 023's "current system" facts against `71d8878` (search SQL already joins the
  address table; `PropertyResponse` uses `address: null`; blank `q` is a 400; the mobile app has no
  production client instance yet; generated schemas are git-ignored).
- Engineering design, decisions table and L1 blocks written into plan 023.
- 023-B1 backend C1 implemented: `PropertySearchProjection` selects `type` and the four address
  parts (plus `hasAddress`) from the existing address join; `PropertyMatch` requires `PropertyType`
  and exposes `Optional<AddressSummary>`; `PropertySearchHitResponse` carries `type` and a nullable
  `AddressSummaryView`; `originalText` and `country` are never selected or exposed.
- Tests added: `PropertyMatchTest` (required type, absent/present address), `PropertySearchIntegrationTest`
  (full, partial and absent address; type on every hit), and two acceptance scenarios (same name told
  apart by address; resident-typed original text absent from search results).
- `docs/api/openapi.json` updated with `AddressSummaryView` and the two new hit fields;
  `docs/DECISION_LOG.md` records P-016.

## Remaining work

- Runner-owned L1 for 023-B1 → lead review → (fix cycle if needed).
- 023-B2 loop.
- Lead: Android device check, cold backend `check` without `-PskipMutation`, root
  `pnpm lint && pnpm test && pnpm typecheck`, then one PR.

## Decisions made

See plan 023, "Engineering design" under 023-B: `address: null` without an address row; a dedicated
application-level `AddressSummary` so `originalText` cannot reach a hit; a small `useSearch` hook with
no new dependency; rows navigate to a temporary placeholder detail route until 023-C; Home becomes the
search entry and keeps the locale switch.

## Assumptions

- Additive response fields do not break `apps/web` (verified by `pnpm --filter @geo-housing/admin-web
  typecheck` in B1's L1).

## Files changed

- `docs/plans/023-mobile-discovery.md` (023-B design, L1 blocks, status, progress log)
- `docs/handoffs/current-task.md` (this file, replacing the stale 023-A handoff)

## Commands and tests

Passed locally/offline:

- `cd apps/api && ./gradlew --offline :modules:properties:test -PskipMutation --rerun-tasks`
- `cd apps/api && ./gradlew --offline :modules:properties:mutationTest --rerun-tasks` (77% threshold met)
- `cd apps/api && ./gradlew --offline :modules:properties:spotlessCheck :app:spotlessCheck :modules:properties:checkstyleMain :modules:properties:checkstyleTest :app:checkstyleTest`
- `cd apps/api && ./gradlew --offline :modules:properties:compileTestJava :app:compileTestJava` (executed as dependencies of the checks above)
- `python3 -m json.tool docs/api/openapi.json` (valid JSON)
- `git diff --check` (no whitespace errors)
- `pnpm --filter @geo-housing/admin-web typecheck` with
  `PATH="$HOME/.nvm/versions/node/v24.18.1/bin:$PATH"` (passed; generated admin-web schema and
  `tsc --noEmit` clean)

Runner-owned L1 results:

- `cd apps/api && ./gradlew :modules:properties:test -PskipMutation --rerun-tasks` — passed
- `cd apps/api && ./gradlew :app:test --tests '*PropertySearchIntegrationTest' --tests '*OpenApiContractIntegrationTest' --tests '*AcceptanceTest' -PskipMutation --rerun-tasks` — passed
- `cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest :modules:properties:spotlessCheck :modules:properties:checkstyleMain :modules:properties:checkstyleTest` — passed
- `pnpm --filter @geo-housing/admin-web typecheck` — runner failed with exit 127 (`pnpm: command
  not found`); passes with the nvm bin directory on `PATH` as recorded above

## Known failures

None. Round 1's `pnpm: command not found` was the lead's runner shell lacking nvm; resolved by
loading `~/.nvm/nvm.sh` before launching the runner (no repo change, no install).

## Risks and unresolved questions

- `:modules:properties` mutation threshold (77) must hold with the new mapping code; checked at lead L2.
- Manual OpenAPI edit mirrors Springdoc's canonical output; the runner's
  `OpenApiContractIntegrationTest` is the authoritative check.
- Native-query projection of `hasAddress` is exercised only by the Docker-backed integration test in
  this sandbox.
- `ResultsScreen` reads `q` via `expo-router`; tests mock it to stay node-only.
- The L1 runner must prepend the nvm Node bin directory (or source nvm) before running
  `pnpm --filter @geo-housing/admin-web typecheck`; otherwise the merge gate stops at exit 127
  despite the check itself passing.

## Human actions required

Founder decision before 023-C: the public property detail response returns `address.originalText`
(free text that may hold an apartment number), and plan 023 maps it as a fallback on the detail
screen. Keep, or remove from the public response? Raised by the 023-B1 review.

## Recommended next action

Inspect the 023-B2 run under `.ai/runs/`. Launch the runner from a shell with nvm loaded
(`. ~/.nvm/nvm.sh`) so `pnpm` L1 steps can run.

## Last updated

2026-11-15

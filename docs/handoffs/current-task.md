# Task handoff

## Objective

Implement plan 012, chunk 1: the admin web app's toolchain plus one real screen — a signed-in
moderator looking at the real moderation queue.

## Active branch

`feat/012-admin-web-chunk1-toolchain`, branched from clean `main` at `e35242c`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/012-admin-web-app.md`, chunk 1 of 3.

## Current status

completed, awaiting independent review

## Completed work

- **Node toolchain reaches the gate.** `scripts/check.sh` sources an nvm-managed Node when one
  exists; a non-interactive shell does not read `~/.bashrc`, so the frontend branch would otherwise
  fail with "corepack is unavailable" on a machine where Node plainly works.
- **pnpm workspace.** Root `package.json` (`pnpm@11.18.0`), `pnpm-workspace.yaml`. Dependency build
  scripts are allowlisted explicitly (`allowBuilds`: esbuild and unrs-resolver yes, sharp no), so a
  new native postinstall is a reviewable change.
- **`apps/web`**: Next.js 15 / React 19 / TypeScript, ESLint flat config via the `FlatCompat`
  bridge (`eslint-config-next` 15.5 is still eslintrc-only), Vitest, Playwright.
- **The contract is pinned.** `OpenApiContractIntegrationTest` canonicalises `/v3/api-docs` and
  byte-compares it to `docs/api/openapi.json` (40 paths, 45 schemas). `-DupdateOpenApiSpec=true`
  regenerates. `apps/web` generates `src/api/generated/schema.d.ts` from that file on every
  `dev`/`build`/`test`/`typecheck`; the generated file is gitignored.
- **Local OIDC provider** (`ghcr.io/navikt/mock-oauth2-server:5.0.2`) in compose, loopback-bound.
- **Sign-in**: authorization-code flow with PKCE via `openid-client`; access token in an httpOnly
  cookie, read only on the server; `/api/auth/{signin,callback,signout}`.
- **Moderation queue** at `/moderation`, a Server Component calling the API server-side.
- **Tests**: 6 Vitest cases over the queue view model; 5 Playwright journeys against the real
  stack. The tampered-session journey found a real defect — a rejected token bounced between `/`
  and `/moderation` forever, fixed with the `/api/auth/expired` route handler.
- Docs: plan 012, `apps/web/README.md`, ARCHITECTURE §Clients, CONTRIBUTING §Required checks,
  DECISION_LOG `P-015`.
- `scripts/validate_repo_governance.py` no longer walks `node_modules` (it was reporting 75
  findings from dependency files).

## Remaining work

Plan 012 chunks 2 (case detail, decide, appeals) and 3 (verification, property, account queues).

## Decisions made

- **The local OIDC provider is not the P-008 vendor choice** (`P-015`). The app speaks only standard
  discovery and authorization-code-with-PKCE, so a vendor is an environment-variable change. Real
  signature validation stays exercised; roles still come from our account table, so a provider that
  mints arbitrary claims cannot mint an administrator.
- **The token never reaches the browser.** Pages call the API from the server. No CORS surface is
  opened for the admin origin, and there is nothing for a cross-site script to steal.
- **`src/moderation/queue.ts` is the allowlist** of what the queue may display. The page renders
  only `QueueRow` fields, so the privacy rule is one testable list rather than scattered JSX.
- **`pnpm e2e` is not in `./scripts/check.sh`.** It needs a running stack; a gate that silently
  skipped would be worse than one that never claimed to cover it.
- **A dead session is cleared, not just redirected past.** `/api/auth/expired` exists because a
  Server Component cannot clear a cookie, and leaving it set is what created the redirect loop.

## Changed files

New: `package.json`, `pnpm-workspace.yaml`, `pnpm-lock.yaml`, `docs/api/openapi.json`,
`docs/plans/012-admin-web-app.md`, all of `apps/web/`, and
`apps/api/app/src/test/java/com/example/geohousing/app/OpenApiContractIntegrationTest.java`.

Modified: `.gitignore`, `scripts/check.sh`, `scripts/validate_repo_governance.py`,
`infra/docker/docker-compose.yml`, `apps/api/app/build.gradle.kts`, `CONTRIBUTING.md`,
`docs/ARCHITECTURE.md`, `docs/DECISION_LOG.md`.

## Commands and tests

```bash
./scripts/check.sh                                    # full gate

docker compose -f infra/docker/docker-compose.yml up -d postgres oidc
cd apps/api && OIDC_JWK_SET_URI=http://localhost:8081/default/jwks \
  IDENTITY_AUTH_SUBJECT_PEPPER=local-dev-only-pepper ./gradlew :app:bootRun
cd apps/web && pnpm e2e                               # 5 passed
```

Both privacy assertions were proven non-vacuous: adding a reporter field to `QueueRow` fails exactly
the Vitest leak test; rendering a reporter name on the page fails exactly the Playwright one. The
contract pin was proven in both directions — passes on a fresh spec, fails on a one-word edit.

## Failures and blockers

None outstanding. Encountered and resolved: pnpm 11 renamed `onlyBuiltDependencies` to `allowBuilds`
and the old key was silently inert; `ghcr.io/navikt/mock-oauth2-server` has no 2.x tag; Playwright
transpiles to CJS so the seed could not use `import.meta`; `-D` properties do not reach the forked
test JVM without explicit forwarding.

## Unresolved risks

- **No way to bootstrap the first administrator.** Both the acceptance suite and the e2e seed write
  the role with SQL. Fine locally; it needs an answer before a production environment exists.
- **The OpenAPI document declares only success responses**, so the generated client types `error` as
  `never` and destructuring a response narrows `response` away (worked around in
  `app/moderation/page.tsx`). Documenting RFC 7807 bodies would fix both.
- **Collision-suffixed `operationId`s** (`queue_1`, `decide_1`, `get_7`) — harmless for a path-based
  client, poor for a reader or a method-per-operation generator.
- The OIDC provider is one more container in an environment that leaks them when a run is killed;
  the `CONTRIBUTING.md` recovery applies.

## Next action

Independent review of this branch by a fresh session that did not implement it, then merge. After
that, plan 012 chunk 2: case detail, decide with reason code, and the appeals queue.

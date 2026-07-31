# Admin web app

The interface moderators, and later administrators, work from. Next.js (App Router) and TypeScript,
per `docs/ARCHITECTURE.md`.

## Where the types come from

Nothing here hand-writes an API shape. `pnpm generate:api` turns `docs/api/openapi.json` into
`src/api/generated/schema.d.ts`, and every script that compiles or runs code regenerates first, so
the client cannot be stale. The spec itself is pinned by `OpenApiContractIntegrationTest` in
`apps/api`: change an endpoint without regenerating the spec and the **backend** build fails, which
is where a contract change should be noticed.

The generated file is not committed — it is a build product of a file that is.

## Where the session lives

Sign-in is a standards-only OIDC authorization-code flow with PKCE (`openid-client`), so choosing an
identity provider later (`P-008`) is a change of environment variables rather than of code.

The access token is stored in an **httpOnly** cookie and read only on the server; pages call the API
from the server, so the token never reaches browser JavaScript and the API needs no CORS opening for
this origin.

## Running it

`infra/docker/docker-compose.yml` includes a **mock OIDC provider** for local use. It signs whatever
it is asked to sign, binds to loopback only, and is not an identity-provider decision. Roles come
from our own account table (ADR-0005), never from the token, so it cannot mint an administrator.

```bash
docker compose -f ../../infra/docker/docker-compose.yml up -d postgres oidc

# API, pointed at the local provider
cd ../api && OIDC_JWK_SET_URI=http://localhost:8081/default/jwks \
  IDENTITY_AUTH_SUBJECT_PEPPER=local-dev-only-pepper ./gradlew :app:bootRun

# this app
cp .env.example .env.local
pnpm dev
```

Sign in at <http://localhost:3000> with any username; the provider will issue a token for it.

## Checks

| Command | What it covers |
| --- | --- |
| `pnpm lint` | ESLint (`next/core-web-vitals`, `next/typescript`) |
| `pnpm typecheck` | regenerates the client, then `tsc --noEmit` |
| `pnpm test` | Vitest over the view models |
| `pnpm e2e` | Playwright, **against a running stack** |

`./scripts/check.sh` runs the first three. It does not run `pnpm e2e`: those tests need Postgres, the
OIDC provider and the API up, and a gate that silently skips when they are absent is worse than one
that never claimed to run them. Run `pnpm e2e` yourself before requesting review on a change to this
app.

`pnpm e2e` seeds its own data through the real API (`e2e/seed.ts`). The one thing it cannot do over
HTTP is grant the first administrator — identity has no bootstrap endpoint — so it writes that role
directly, exactly as the backend acceptance suite does.

# Task handoff

## Objective

Implement plan 012, chunk 3: the remaining admin queues — verification decisions with the document
in front of the moderator, and property lifecycle actions.

## Active branch

`feat/012-admin-web-chunk3-other-queues`, branched from clean `main` at `9506f6a`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/012-admin-web-app.md`, chunk 3 of 3.

## Current status

**completed with one blocked verification step** — see Failures and blockers.

## Completed work

- **Verification queue** (`/verification`) and case detail (`/verification/[caseId]`): claim,
  method, status, tier, evidence list, and approve / reject / revoke carrying the version.
- **Evidence proxy** (`/api/evidence/[caseId]/[evidenceId]`): adds the bearer server-side and
  streams straight through. Nothing stored; `no-store`, `nosniff`, `attachment`, `no-referrer` and
  a `default-src 'none'; sandbox` CSP asserted here rather than merely forwarded. Every view still
  reaches the API and lands in its access audit.
- **Property search and lifecycle** (`/properties`, `/properties/[propertyId]`): find a property,
  then activate / withdraw / merge, with only the actions that status allows offered.
- **`MINIO_KMS_SECRET_KEY` added to compose.** Local evidence upload had never worked: the store
  writes with server-side encryption and MinIO answered 501 without a key. The backend tests set it
  on their own container, which is why the suite never noticed.
- **All four server actions bound rather than wrapped** (`action.bind(null, id)`), including the two
  from chunk 2. A closure around a server action cannot be submitted before hydration, and an early
  click was being swallowed silently.
- 46 Vitest cases; 13 of 16 Playwright journeys passing.

## Remaining work

None in plan 012. Two backend gaps are recorded as their own work: an audited account-role endpoint,
and a status-filtered admin property listing.

## Decisions made

- **Evidence is proxied through this app, not linked** (founder decision, 2026-08-01). The plan
  previously forbade proxying; checking the API showed no linkable form exists — `readEvidence`
  streams bytes and its own comment calls the proxy deliberate. With the token httpOnly on the Next
  origin, a browser link to the API cannot authenticate.
- **Account role management is not built** (founder decision, 2026-08-01). No endpoint exists.
- **Only permitted lifecycle actions are offered.** A button the API will refuse teaches an
  administrator to ignore the buttons.

## Changed files

New: `apps/web/src/verification/{case.ts,case.test.ts,actions.ts}`,
`apps/web/src/properties/{property.ts,property.test.ts,actions.ts}`,
`apps/web/app/verification/{page.tsx,[caseId]/page.tsx,[caseId]/decide-form.tsx}`,
`apps/web/app/properties/{page.tsx,[propertyId]/page.tsx,[propertyId]/lifecycle-form.tsx}`,
`apps/web/app/api/evidence/[caseId]/[evidenceId]/route.ts`,
`apps/web/e2e/verification-and-properties.spec.ts`.

Modified: `infra/docker/docker-compose.yml`, `apps/web/e2e/seed.ts`,
`apps/web/app/moderation/page.tsx`, `apps/web/src/moderation/actions.ts`,
`apps/web/app/moderation/[caseId]/decide-form.tsx`,
`apps/web/app/moderation/appeals/hear-appeal-form.tsx`, `docs/plans/012-admin-web-app.md`.

## Commands and tests

```bash
./scripts/check.sh

docker compose -f infra/docker/docker-compose.yml up -d postgres oidc minio
docker run --rm --network host --entrypoint sh minio/mc \
  -c "mc alias set local http://localhost:9000 geo_housing_evidence geo_housing_evidence \
      && mc mb --ignore-existing local/verification-evidence"
cd apps/api && OIDC_JWK_SET_URI=http://localhost:8081/default/jwks \
  IDENTITY_AUTH_SUBJECT_PEPPER=local-dev-only-pepper ./gradlew :app:bootRun
cd apps/web && pnpm e2e
```

## Failures and blockers

**Three verification journeys are unverified.** They need evidence uploaded to MinIO, and the
compose MinIO cannot be recreated to pick up `MINIO_KMS_SECRET_KEY` because Docker is in the wedged
state `CONTRIBUTING.md` describes — `docker compose up -d --force-recreate minio` fails with
`cannot stop container: permission denied`. 78 orphaned Testcontainers from this session's gate runs
are stuck the same way. The compose value is copied from `EvidenceEndpointIntegrationTest`, which
passes in the backend suite, but **it has not been run end to end**.

```text
HUMAN_ACTION_REQUIRED
Command: sudo systemctl restart docker && pgrep -c docker-proxy && sudo pkill -f docker-proxy
Reason: orphaned Testcontainers cannot be killed even under sudo, and the compose MinIO cannot be
        recreated to pick up the KMS key that makes evidence upload work locally.
Expected result: container count drops to the compose services only; no stray docker-proxy.
Verification command: docker ps -q --filter label=org.testcontainers=true | wc -l   # expect 0
```

After the restart, re-run `docker compose ... up -d minio`, recreate the bucket, and
`cd apps/web && pnpm e2e` — all 16 journeys should pass.

## Unresolved risks

- **A submit that lands before hydration is still dropped** in a dev build. Binding the actions
  narrows the window and is correct regardless, but does not close it; the property journey waits
  for the route to settle and says why. Worth re-checking against a production build.
- **No account-role endpoint** and **no admin property listing** — see the plan's findings.
- The moderation enums are undocumented in OpenAPI, so the client restates `DecisionAction`.
- The OpenAPI document declares only success responses.

## Next action

The human action above, then re-run `pnpm e2e` to confirm the three evidence journeys. Then
independent review of this branch by a fresh session that did not implement it.

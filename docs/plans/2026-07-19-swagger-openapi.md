# Swagger and OpenAPI Documentation

Status: Active
Owner: Codex
Related issue: none (direct request)
Last updated: 2026-07-19

## Objective

Expose the Spring Boot API's generated OpenAPI document and interactive Swagger UI without
weakening authentication for application endpoints.

## Acceptance criteria

- [x] The application publishes a generated OpenAPI 3 document with service metadata and a JWT
      bearer security scheme.
- [x] Swagger UI and its OpenAPI JSON/YAML routes are publicly readable.
- [x] Existing `/api/**` endpoints remain authenticated and the documentation routes have
      integration-test coverage.
- [x] Backend checks and repository checks pass.

## Non-goals

- Hand-authoring a static OpenAPI specification or generated client.
- Adding endpoint-by-endpoint descriptions beyond the metadata Springdoc can infer today.
- Exposing actuator endpoints or changing application authorization.

## Current system

The `app` module is the Spring Boot 4.1 composition root. `SecurityConfiguration` permits only
`/actuator/health` anonymously and otherwise uses a stateless JWT resource server. `docs/API_GUIDELINES.md`
sets OpenAPI as the client contract.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| OpenAPI provider | `springdoc-openapi-starter-webmvc-ui` 3.0.3 | The Springdoc Boot-4 documentation specifies this WebMVC UI starter; it supplies generated `/v3/api-docs` and Swagger UI. | Spring Boot or Springdoc upgrade |
| Documentation access | Permit only Swagger UI and OpenAPI routes | The contract needs to be discoverable, but exposing it must not make business or actuator routes public. | API publication policy changes |
| Auth contract | Global HTTP bearer JWT security scheme | The UI can authorize calls while the server remains authoritative for authentication and roles. | Additional authentication mechanisms |

## Implementation steps

1. Add the compatible Springdoc UI starter to the app module.
2. Configure service metadata and the bearer security scheme at the composition root.
3. Permit the documentation assets/routes in Spring Security and document their policy.
4. Cover public docs and protected API behavior with an integration test.
5. Run focused Gradle and repository checks.

## Verification

```bash
cd apps/api
./gradlew :app:test --tests '*SwaggerEndpointIntegrationTest*'
cd /home/vladimir/IdeaProjects/geo-housing-reviews
./scripts/check.sh
```

The integration test asserts anonymous access to `/v3/api-docs` and Swagger UI, verifies the
bearer scheme in the contract, and confirms `/api/me` still rejects anonymous requests.

## Risks and rollback/forward-fix

Swagger UI adds a frontend dependency and publishes controller signatures. If its routes or
security configuration cause an issue, remove the dependency and the explicitly permitted route
set; no data or schema migration is involved. Review the generated document whenever public API
surface changes so it does not imply that verification proves review claims.

## Progress log

- 2026-07-19: Inspected the Spring Boot composition root, security chain, API guidelines, and test
  conventions. Selected Springdoc 3.0.3 based on Springdoc's Spring Boot 4 documentation.
- 2026-07-19 (Codex): Implemented — Springdoc WebMVC UI starter, `OpenApiConfiguration` (metadata +
  global HTTP bearer JWT scheme), documentation routes permitted in `SecurityConfiguration`,
  `SwaggerEndpointIntegrationTest`, README + API_GUIDELINES. Tests left unrun.
- 2026-07-20 (Claude Code takeover): Ran the checks Codex deferred and drove the running app.
  Springdoc 3.0.3 resolves and works on Boot 4.1; `/v3/api-docs` (JSON) and `/swagger-ui/index.html`
  are public, `/api/me` stays 401, and the generated document lists the real identity operations.
  **Found and fixed a gap:** `/v3/api-docs.yaml` returned 401 — API_GUIDELINES advertised it as
  public but the `/v3/api-docs/**` matcher does not cover the `.yaml` sibling path. Added
  `/v3/api-docs.yaml` to the permitted matchers and a regression test
  (`publishesTheYamlOpenApiDocumentWithoutAuthentication`). `./scripts/check.sh` passes; the Swagger
  test is 4/4. Committed on `feat/swagger-openapi` (Codex had left the work uncommitted on `main`).

## Final outcome

Complete, pending review and merge. Implemented by Codex; finished and verified by Claude Code
(distinct authors), on branch `feat/swagger-openapi`.

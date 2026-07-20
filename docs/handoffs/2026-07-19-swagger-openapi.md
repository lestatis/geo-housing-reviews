# Task handoff

## Objective

Add generated OpenAPI documentation and Swagger UI to the Spring Boot API while preserving JWT
protection for all business endpoints.

## Active branch

`main`

## Related issue or plan

`docs/plans/2026-07-19-swagger-openapi.md`

## Current status

in_progress

## Completed work

- Inspected the composition root, Spring Security policy, API guidelines, Gradle conventions, and
  integration-test setup.
- Selected Springdoc `springdoc-openapi-starter-webmvc-ui` 3.0.3, which Springdoc documents for
  Spring Boot 4 WebMVC applications.

## Remaining work

- Add Springdoc, the OpenAPI metadata/security-scheme configuration, route authorization,
  documentation, and integration coverage.
- Run focused and repository checks.

## Decisions made

- Swagger UI and OpenAPI routes will be public documentation only; `/api/**` and actuator policy
  remain unchanged.
- The generated contract will specify HTTP bearer JWT authentication globally.

## Assumptions

- The requested "Swagger" means interactive Swagger UI plus an OpenAPI 3 document, not a static
  hand-maintained file.

## Files changed

- `docs/plans/2026-07-19-swagger-openapi.md`
- `docs/handoffs/2026-07-19-swagger-openapi.md`

## Commands run

- Read-only repository inspection and Springdoc official documentation lookup.

## Tests and verification

Not yet run.

## Known failures

None.

## Risks and unresolved questions

- Public documentation exposes endpoint shapes by design, but must not expose an actuator route or
  bypass JWT authorization.

## Human actions required

None.

## Recommended next action

Implement the scoped Springdoc integration and run the focused integration test.

## Last updated

2026-07-19

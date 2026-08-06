---
paths:
  - "apps/api/**/*.java"
  - "apps/api/**/*.kt"
  - "apps/api/**/build.gradle*"
  - "apps/api/**/pom.xml"
---

# Backend Java rules

- Prefer Java records for immutable transport/value data where appropriate, not for mutable JPA entities.
- Keep domain logic outside controllers and persistence adapters.
- Validate at boundaries and enforce invariants in domain/application code.
- Use constructor injection.
- Avoid broad `@Transactional`; define transaction boundaries at application use cases.
- Do not expose JPA entities through APIs.
- Every repository query must be scoped to the owning module and tenant/authorization context where applicable.
- Use Testcontainers for behavior depending on PostgreSQL/PostGIS.
- Migrations are append-only after merge; fix forward rather than editing applied migrations.
- Time-dependent behavior uses an injected clock.
- A response field that can be null needs `@Schema(nullable = true)`. Springdoc assumes not-null, so
  without it the generated client declares a type the API contradicts — and the fields most likely to
  be null are the ones where null carries meaning ("no appeals were heard" is not "none were
  overturned"). Missed twice: on `AdminMetrics` and again on the audit response two commits later.
- Check what the annotation actually produced. `@Schema(requiredMode = REQUIRED)` on a record
  component does **not** reach `docs/api/openapi.json`; the field stays optional in the client. Read
  the regenerated spec rather than trusting the annotation.
- Declaring one `@ApiResponse` replaces springdoc's derived set instead of adding to it. Document a
  400 and the 200 disappears, taking the response schema out of the generated client with it.

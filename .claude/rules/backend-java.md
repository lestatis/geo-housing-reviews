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

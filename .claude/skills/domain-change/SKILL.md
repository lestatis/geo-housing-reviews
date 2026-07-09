---
name: domain-change
description: Safely change domain entities, state machines, persistence mappings or public contracts.
---

# Domain change

Before editing:

- read `docs/DOMAIN_MODEL.md`, `docs/ARCHITECTURE.md`, and the relevant ADRs;
- identify aggregate ownership, invariants, state transitions and module boundaries;
- list affected API contracts, events, migrations, moderation/audit behavior and tests.

Implementation rules:

- use opaque identifiers externally;
- enforce invariants in the owning module, not only in controllers/UI;
- add backward-compatible migration steps whenever possible;
- never silently reinterpret historical review/verification data;
- update OpenAPI and examples with the implementation;
- add tests for old data, invalid transitions, authorization and rollback/compatibility assumptions;
- write an ADR when the decision is costly to reverse or affects multiple modules.

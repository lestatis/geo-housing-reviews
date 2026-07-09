# Architecture rules

- The backend is a modular monolith until an accepted ADR says otherwise.
- Modules own their data and expose explicit application interfaces.
- Do not access another module's repository/table/entity directly.
- Do not add infrastructure components “for future scale” without measured need.
- New cross-cutting abstractions require at least two concrete current use cases.
- Keep shared-kernel code tiny and domain-neutral.
- Any architecture boundary change updates `docs/ARCHITECTURE.md` and an ADR.

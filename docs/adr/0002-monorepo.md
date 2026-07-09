# ADR-0002: Use a monorepo for product applications

Status: Accepted
Date: 2026-07-09
Deciders: Founders

## Context

The mobile app, admin app, backend, generated contracts and design tokens evolve together. The team is small and values atomic changes and AI-agent context routing.

## Decision

Use one repository with `apps/`, `packages/`, `infra/`, `docs/` and agent configuration. Keep application build systems independent but expose root validation commands.

## Consequences

### Positive

- atomic API/client changes;
- one PR for a vertical feature;
- shared documentation and governance;
- easier local setup and agent instructions.

### Trade-offs

- CI must avoid rebuilding everything unnecessarily;
- root instructions must stay concise;
- nested rules may be needed as applications grow.

## Revisit when

Repository size, access boundaries, release ownership or CI duration becomes a measured problem.

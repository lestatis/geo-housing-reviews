# ADR-0003: Model verification as a typed trust signal

Status: Accepted
Date: 2026-07-09
Deciders: Founders

## Context

Different users can prove different relationships using evidence with different strengths. A boolean `verified` hides meaning and can mislead readers into thinking the review text itself is certified.

## Decision

Store verification method, relationship type, strength tier, decision, expiration/revocation and policy version. Publish only a safe badge explaining what was checked. Use the signal as one ranking factor.

## Consequences

### Positive

- clearer user expectations;
- multiple methods can evolve;
- better auditability and fraud controls;
- unverified users are not excluded.

### Trade-offs

- more complex data model and UI;
- moderation policies require versioning;
- ranking must avoid over-weighting verification.

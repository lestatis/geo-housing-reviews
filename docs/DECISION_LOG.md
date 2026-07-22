# Decision Log

Use ADRs for technical decisions. This file tracks founder/product decisions and unresolved questions.

## Open decisions

| ID | Decision | Options/notes | Owner | Deadline |
|---|---|---|---|---|
| P-010 | Legal entity and moderation jurisdiction | required before public launch | founders | pre-launch |

## Accepted product decisions

| ID | Decision | Date | Reason |
|---|---|---|---|
| P-A01 | Verified reviews rank above comparable unverified reviews but unverified reviews remain visible | 2026-07-09 | trust without excluding users who cannot safely provide evidence |
| P-A02 | Listings are a separate future module | 2026-07-09 | protect MVP scope and review integrity |
| P-A03 | Verification confirms relationship, not truth of all claims | 2026-07-09 | accurate user expectation and safer moderation model |
| P-001 | Launch city: Batumi only | 2026-07-09 | concentrates catalogue and moderation effort in one market for initial launch |
| P-002 | Launch languages: Russian and English active at launch; Georgian data model stays ready but Georgian-language content is not solicited or published until a Georgian-reading moderator is available | 2026-07-09 | moderators must be able to read what they review; matches founder language capability and Batumi's language mix |
| P-003 | Public anonymity: pseudonym by default for reviewers; verified real/organization identity required for representative accounts | 2026-07-09 | protects reviewer safety while keeping representative replies accountable |
| P-004 | Initial verification methods: ship Tier 1 (location/invitation signal) and Tier 2 (document-assisted) together at launch | 2026-07-09 | avoids excluding users without documentary evidence while still offering a strong trust signal, consistent with P-A01 |
| P-005 | Review moderation mode: pre-moderation for all reviews at launch | 2026-07-09 | all accounts are new/untrusted at launch per moderation policy criteria; revisit once a trusted-contributor cohort exists |
| P-006 | Property granularity: two-level hierarchy (residential complex → building) | 2026-07-09 | matches existing domain model fields (`type`, `parent_property_id`); supports both building- and complex-level comparison |
| P-007 | Aggregate score: hybrid — categories always shown; single overall number displayed only once a minimum review-count threshold is met | 2026-07-09 | matches PRD's data-sufficiency requirement and reduces manipulation risk on low-review properties |
| P-008 | Auth provider: managed OIDC/IdP provider (specific vendor pending ADR) | 2026-07-09 | offloads security-critical auth/session/MFA handling appropriate for a team just starting; requires a follow-up ADR before integration |
| P-009 | Map/geocoding provider: OpenStreetMap-based stack | 2026-07-09 | fits the existing PostGIS-first architecture and avoids a paid dependency before demand is proven |
| P-011 | Public read access: the property catalogue and published reviews are readable without an account; all writes, self-service and admin endpoints require authentication | 2026-07-22 | a review platform must be browsable to be trusted (and indexed); module visibility rules already treat the anonymous viewer as a normal case, so unpublished content stays hidden either way |

## How to update

Add a decision when founders explicitly choose. Link an issue/design/ADR. Do not silently infer a product decision from a temporary implementation shortcut.

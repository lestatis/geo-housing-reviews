# Reviews Module: Structured Reviews, Versions, Ratings, Publication State

Status: Active
Owner: Claude Code
Related issue: none (direct founder request; follows the properties module, plan 004)
Last updated: 2026-07-20

## Objective

Implement the `reviews` backend module: structured reviews of a property with immutable content
versions, category ratings, a publication lifecycle, and a verified/unverified experience signal.
This is MVP loop 2 ("Submit experience → publish safely") and the heart of the product.

## Acceptance criteria

- [ ] `reviews` schema owns its tables under `V4.x`; `property_id`/`author_account_id` are opaque —
      no cross-module foreign keys.
- [ ] A `Review` aggregate with a publication state machine and **immutable** `ReviewVersion`s: an
      edit appends a version and moves `current_version_id`, preserving the edit trail.
- [ ] Category ratings with `not_applicable` and a versioned category set.
- [ ] Verification is stored as a **summary projection** (default `UNVERIFIED`); Tier 0 still
      publishes. Reviews never assert that a review's statements are true.
- [ ] `POST /api/properties/{id}/reviews`, `GET /api/reviews/{id}`, and a **cursor-paginated**
      `GET /api/properties/{id}/reviews`.
- [ ] The property reference is validated through the properties module's public contract
      (`properties.api`) — no direct table access.
- [ ] Admin/moderation state transitions are audited.

## Non-goals

Moderation *decisions*, reports, appeals and right-of-reply (the `moderation` module); verification
cases and evidence (the `verification` module — reviews only projects a summary); ranking algorithms;
media attachments (`media`); machine translation; helpful-vote anti-abuse tuning.

## Module-wide decisions

| Decision | Choice | Reason |
|---|---|---|
| Migration namespace | `reviews` schema, `V4.x` (root=1, identity=2, properties=3, reviews=4) | Module owns its tables |
| Cross-module refs | `property_id` / `author_account_id` are opaque UUIDs, no FKs; property checked via `properties.api` | No module reads another module's tables |
| Immutable versions | An edit appends a `review_version`; `review.current_version_id` moves | DOMAIN_MODEL "immutable content version"; keeps the edit trail for moderation |
| Verification | A summary projection only (`UNVERIFIED` / `RELATIONSHIP_SIGNAL` / `DOCUMENT_VERIFIED`); Tier 0 publishes | TRUST_VERIFICATION §2 (never a bare `verified=true`), §3 |
| Reviews per author | **One live review per (author, property)**, partial unique index over non-terminal statuses; edits append versions | Founder decision; makes "review versions" coherent and keeps aggregates honest |
| Relationship types | `CURRENT_RESIDENT` / `FORMER_RESIDENT` / `OWNER` / `OTHER` | Founder decision; small enough to verify/moderate, `OTHER` avoids forcing a false claim |
| Listing | Cursor pagination | API_GUIDELINES names reviews explicitly |

## Reviews migration registry (append-only)

| Version | Contents | Chunk |
|---|---|---|
| `V4.1` | `reviews` schema; `review`, `review_version`, `category_rating` | 1 |
| `V4.2` | reserved — moderation/admin audit table | 7 |

## Implementation chunks (one branch each: self-check → fresh independent review → merge before the next)

1. **Foundation + schema** (this branch): module deps, `V4.1`, migration integration test, this plan;
   also closes plan `004`.
2. **Domain model**: `Review` aggregate + state machine (DRAFT → PENDING_MODERATION → PUBLISHED /
   REJECTED / HIDDEN / REMOVED), `ReviewVersion`, `CategoryRating`, `RelationshipType`,
   `ResidencePeriod`, `VerificationSummary`; activates the reviews ArchUnit domain rules.
3. **Application layer**: repository ports, a `PropertyLookup` outbound port, submission/edit/query
   services; in-memory-fake unit tests.
4. **`properties.api` + adapter**: the minimal published contract in the properties module and the
   reviews-side adapter — **completes plan `004` chunk 8 with a real consumer**, and is the first
   cross-module call in the codebase.
5. **Persistence adapters**: JPA for the review/version/rating graph; integration tests.
6. **Public endpoints**: create/get/list with cursor pagination; RFC 7807 handler scoped to reviews.
7. **Moderation-state transitions + audit** (`V4.2`), mirroring properties chunk 7.
8. **Helpful signals / ranking inputs** — MVP Should-have; likely deferred.

## Verification

```bash
cd apps/api
./gradlew :modules:reviews:check
./gradlew :app:test
cd /home/vladimir/IdeaProjects/geo-housing-reviews && ./scripts/check.sh
```

## Progress log

- 2026-07-20: Chunk 2 (domain model) implemented on `feat/005-reviews-chunk2-domain`, branched from
  `main` after chunk 1 (`8142f88`). Framework-free `reviews.domain`: `ReviewId`, `AuthorId` and
  `PropertyRef` (opaque cross-module references — no identity/properties dependency),
  `RelationshipType` / `ReviewStatus` / `VerificationTier` / `Recommendation` enums,
  `ResidencePeriod` and `CategoryRating` value objects, the immutable `ReviewVersion` record
  (aggregate-assigned sequential numbering, non-blank body, unique categories), and the `Review`
  aggregate. State machine: DRAFT → PENDING_MODERATION (submit; an empty draft cannot be submitted)
  → PUBLISHED (publish) / REJECTED (terminal); PUBLISHED ⇄ HIDDEN (hide/restore); any live state →
  REMOVED (terminal). Terminal reviews reject all mutation — the author starts fresh, matching the
  partial unique index. Two domain decisions worth flagging for review: **editing a published review
  sends it back to PENDING_MODERATION** (pre-publication moderation is the MVP default per
  MODERATION.md, so changed content is re-checked before being public again), and **`publishedAt`
  preserves the first publication time** across re-moderation cycles. The verification tier is a
  mutable projection (`updateVerificationTier`), defaulting to UNVERIFIED. Invariants mirror `V4.1`
  (PUBLISHED requires `publishedAt`; sequential version numbers) and are enforced in `create` and
  `reconstitute`. The domain layer is stricter than the schema in one place: every version after the
  first must state an edit reason. ArchUnit's domain-purity rules now run against `reviews.domain`
  and pass. 17 unit tests; `./scripts/check.sh` passes.
- 2026-07-20: Plan approved (plan mode; new module, migrations, public API). Two product decisions
  confirmed with the founder: one live review per author per property (edits append versions), and the
  four-value relationship vocabulary. Chunk 1 implemented on `feat/005-reviews-chunk1-foundation`:
  `V4.1` creates the `reviews` schema and the `review` / `review_version` / `category_rating` tables.
  Every constraint was exercised directly against dev Postgres before the test was written — the
  partial unique index (a second live review is rejected, but a new one is allowed once the first is
  `REMOVED`), the residence-period ordering, "a published review must have `published_at`", per-review
  version numbering, non-blank body, the recommendation vocabulary, and the rating rule that a
  category is either `not_applicable` or carries a 1–5 value. The circular
  `review.current_version_id` foreign key is added after both tables exist.

## Final outcome

Not yet complete.

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

- 2026-07-22: Chunk 5 (persistence) implemented on `feat/005-reviews-chunk5-persistence`. JPA for the
  review/version/rating graph: `ReviewJpaEntity` with cascaded unidirectional `@OneToMany`s
  (`review_id`, `review_version_id`), `@Version` optimistic locking, and `property_id` /
  `author_account_id` as plain UUID columns rather than associations.
  **`V4.2__defer_review_current_version_fk.sql`** — the review and its versions reference each other,
  so one FK has to be checked at commit; deferring `review_current_version_fkey` lets one flush write
  the whole aggregate. Without it the adapter would have to insert, flush, then UPDATE
  `current_version_id`, and that second UPDATE would bump the optimistic-lock version so a freshly
  created review would look stale to its own creator. Verified against a scratch Postgres before
  writing any Java: the circular insert is accepted, a dangling `current_version_id` still fails at
  the deferred check, and `review_version.review_id` stays immediate.
  `JpaReviewRepository.save` loads the stored entity and *applies* the aggregate's changes rather
  than merging a detached copy, with a `requireAppendOnly` guard asserting that stored versions are
  never rewritten or dropped — content immutability is a moderation guarantee, so the adapter
  refuses to be the place it breaks. Listing uses keyset pagination in two steps (page the ids with
  the limit in SQL, then fetch-join versions for those ids) because Hibernate pages a collection
  fetch join in memory; ratings use `@BatchSize` instead of a second bag fetch. Services are now
  wired (`ReviewsBeanConfiguration`), and `reviews.infrastructure.persistence` is added to the app's
  `@EntityScan`/`@EnableJpaRepositories`. **Known limitation:** the listing loads every version of
  every review because `Review.reconstitute` enforces sequential numbering, so a partial load would
  fail its own invariant; a read model for listings is the right fix once ranking needs one.
  9 persistence integration tests + 1 migration test on real Postgres (round-trip, edit appends,
  stale write refused, the partial unique index as backstop, rejected review frees the slot, paging
  including same-instant ties); `./scripts/check.sh` passes.
- 2026-07-22: Chunk 4 (`properties.api` + adapter) implemented on
  `feat/005-reviews-chunk4-property-lookup`. **This closes plan 004 chunk 8** and is the first
  cross-module call in the codebase. Published contract (properties module): `PropertyCatalog` with a
  single `findSurviving(UUID)`, `PropertySummary(propertyId, canonicalName, visibility)` and
  `PropertyVisibility{PUBLIC, WITHHELD}`. Two deliberate narrowings: (a) the api **resolves merges**
  so no consumer ever sees a merged property or has to follow the chain itself — a merged property is
  an alias, and content should attach to the building that is still real; (b) the api exposes coarse
  *visibility* rather than the four-state internal lifecycle, so DRAFT/ACTIVE both read as PUBLIC
  (matching what the public HTTP API already exposes) and the lifecycle stays free to change.
  Reviews-side `CatalogPropertyLookup` (`@Component`) maps a summary to `PropertyReviewability`;
  **the policy "withheld properties take no new reviews" lives in reviews, not properties** — that
  split is the point of the boundary. Gradle: `modules/reviews` now depends on `modules:properties`.
  Verified that ArchUnit's api-only rule genuinely bites by adding a deliberate `properties.domain`
  import and watching `modules_should_only_be_reached_through_their_api_package` fail, then reverting.
  **Defect found in properties, not fixed here:** `Property.mergeInto` never checks its target, so an
  A→B / B→A cycle is possible; `PropertyCatalogService` caps at 8 hops and throws
  `UnresolvableMergeChainException` (loud, not a silently dropped review), with a regression test.
  Guarding the admin merge path is a properties follow-up. 11 unit tests plus a cross-module
  integration test on real Postgres (`PropertyLookupIntegrationTest`: real property, merged property
  resolving to the survivor, absent property); `./scripts/check.sh` passes.
- 2026-07-20: Chunk 3 (application layer) implemented on `feat/005-reviews-chunk3-application`.
  Ports: `ReviewRepository` (by id, the live review for an author+property mirroring the partial
  unique index, create, save, and a cursor-paginated published listing) and the `PropertyLookup`
  outbound port returning `PropertyReviewability(reviewTarget, acceptsNewReviews)` — the smallest
  question reviews needs answered, and the contract chunk 4 will satisfy from `properties.api`.
  `ReviewCursor` (publishedAt desc + review id tie-break) and `ReviewPage` are defined here so chunk 6
  only encodes them for HTTP. Use cases: `ReviewSubmissionService.submit` (property must exist and
  accept reviews; the review attaches to the **merge-surviving** property; one live review per author
  per property, refused with the existing review's id) and `.edit` (append a version with a required
  reason; a published review returns to moderation). `ReviewQueryService.getById` + `listPublished`
  (clamped to 50). Decisions worth review: (a) **visibility is one shared rule** (`ReviewVisibility`)
  — only PUBLISHED is public, everything else is author/moderator-only and reported as **not found**
  rather than forbidden, so a stranger cannot probe whether a person reviewed a building; (b) editing
  someone else's *visible* review is a 403 while editing an invisible one is a 404 — the distinction
  never leaks existence; (c) **a HIDDEN review cannot be edited by its author** — a moderator withheld
  it, and a silent rewrite would make moderation a negotiation it cannot see; appeals belong to the
  moderation module. Note for chunk 6: API_GUIDELINES requires `Idempotency-Key` on final review
  submission — the one-live-review rule already makes a replayed submit safe (it is refused with the
  existing id), so the web layer only has to map that to a stable response. 22 new unit tests against
  in-memory fakes, including the negative authorization paths; `./scripts/check.sh` passes.
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

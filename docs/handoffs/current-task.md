# Task handoff

## Objective

Build the `reviews` backend module (plan `docs/plans/005-reviews-module.md`): structured reviews of a
property with immutable content versions, category ratings, a publication lifecycle, and a
verified/unverified experience signal. This is chunk 3 (application layer).

## Active branch

`feat/005-reviews-chunk3-application` (branched from `main` at `48e798e`)

## Related issue or plan

No issue. See `docs/plans/005-reviews-module.md` — this is chunk 3 of 8.

## Current status

chunk3_implemented — ready for fresh independent review and merge before chunk 4.

## Completed work

On `main`: identity module complete (plan `002`), the Swagger/OpenAPI feature, and the **properties
module complete at 7/8** (plan `004`, ending `34d7144`).

### Plan 004 closed (this branch)

`docs/plans/004-properties-module.md` is marked **Complete (7/8)**. Chunk 8 (`properties.api`) was
deliberately not built there: ARCHITECTURE requires a concrete consumer before a cross-module
abstraction exists (the same reasoning that kept `identity.api` an empty stub). The consumer is the
reviews module, so the contract is built in plan `005` chunk 4, against a real caller. Deferred
properties items are listed in that plan's Final outcome rather than dropped.

### Reviews chunk 3 — application layer (this branch)

Framework-free use cases and the ports they need:
- `ReviewRepository` — by id; the live review for an author+property (mirrors `V4.1`'s partial unique
  index, so REJECTED/REMOVED do not block a fresh start); create; save; cursor-paginated published
  listing. `ReviewCursor` (publishedAt desc, review id tie-break) and `ReviewPage` live here; the
  opaque HTTP cursor encoding is chunk 6's job.
- `PropertyLookup` outbound port → `PropertyReviewability(reviewTarget, acceptsNewReviews)`. Chunk 4
  implements it over `properties.api`. `reviewTarget` is the merge-surviving property, so reviews of
  one building keep landing on one record.
- `ReviewSubmissionService.submit` / `.edit`, `ReviewQueryService.getById` / `.listPublished`.

**Points a reviewer should push on:**
- Visibility is a single shared rule (`ReviewVisibility`) applied by every id-based path. Only
  PUBLISHED is public; anything else is author/moderator-only and reported **not found**, never
  forbidden — a 403 would confirm that a given person reviewed a given building.
- Editing another user's *visible* (published) review is 403; editing an invisible one is 404.
- A HIDDEN review cannot be edited by its author (a moderator withheld it; appeals belong to the
  moderation module). Terminal states are refused by the aggregate.
- Chunk 6 note: `Idempotency-Key` on final submission is satisfied in substance by the
  one-live-review rule — a replay is refused with the existing review's id.

### Reviews chunk 2 — domain model (merged to `main`)

Framework-free `reviews.domain` (ArchUnit's domain-purity rules now apply to it and pass):
- Opaque references: `AuthorId` (identity account) and `PropertyRef` (properties module) — no
  cross-module type or table dependency; the property's reviewability is checked via `properties.api`
  in chunk 4.
- The `Review` aggregate holds its immutable `ReviewVersion`s (aggregate-assigned sequential
  numbers; the current version is the last). State machine: DRAFT → PENDING_MODERATION (submit;
  empty drafts cannot be submitted) → PUBLISHED / REJECTED(terminal); PUBLISHED ⇄ HIDDEN; any live →
  REMOVED(terminal). Terminal states reject all mutation, matching the one-live-review index.
- **Two domain decisions to check in review:** editing a PUBLISHED review returns it to
  PENDING_MODERATION (pre-publication moderation is the MVP default, MODERATION.md), and
  `publishedAt` keeps the *first* publication time across re-moderation. Both are documented on the
  aggregate.
- The domain is stricter than the schema in one place: every version after the first must state an
  `editReason` (the schema allows null).
- `VerificationTier` (UNVERIFIED / RELATIONSHIP_SIGNAL / DOCUMENT_VERIFIED) is a mutable projection
  updated via `updateVerificationTier`; it never claims the review's statements are true.

### Reviews chunk 1 — foundation + schema (merged to `main`)

- `modules/reviews/build.gradle.kts`: Spring Boot BOM + `spring-boot-starter-data-jpa` + `assertj`
  (web deferred to chunk 6), matching how properties started.
- `V4.1__create_review_tables.sql` creates the `reviews` schema and three tables:
  - `review` — `property_id` and `author_account_id` are **opaque UUIDs, not foreign keys** (they
    belong to other modules); `relationship_type` and `status` CHECKs; a residence period that cannot
    end before it starts; `verification_tier` defaulting to `UNVERIFIED` (a projection — the
    verification module decides, and Tier 0 still publishes); "a `PUBLISHED` review must record
    `published_at`"; optimistic-locking `version`.
  - `review_version` — immutable content: `version_number` unique per review, non-blank `body`,
    `recommendation` vocabulary, `edit_reason` (null for the first version).
  - `category_rating` — either `not_applicable` **or** a 1–5 value (never both, never neither), one
    row per (version, category), and a `category_set_version` so historical ratings stay
    interpretable.
  - The circular `review.current_version_id` FK is added after both tables exist.
  - A **partial unique index** enforces one live review per (author, property) over non-terminal
    statuses, so an author edits (appending a version) rather than posting twice — but may start
    fresh after a `REJECTED`/`REMOVED` one.

## Remaining work

Chunks 4–8 (see the plan): domain model + state machine (2); application layer with a `PropertyLookup`
outbound port (3); **`properties.api` + adapter — the first cross-module call, closing plan 004's
chunk 8** (4); persistence (5); public endpoints with cursor pagination (6); moderation-state
transitions + audit, `V4.2` (7); helpful signals/ranking inputs, likely deferred (8).

## Decisions made

- One live review per (author, property); edits append immutable versions. **Confirmed with the
  founder.**
- `RelationshipType` = CURRENT_RESIDENT / FORMER_RESIDENT / OWNER / OTHER. **Confirmed with the
  founder.**
- Verification is stored as a summary projection only, never a bare `verified=true`
  (TRUST_VERIFICATION §2); Tier 0 content publishes.
- Reviews owns publication *state*; the moderation module will own *decisions*, reasons and appeals.
- Review listings will be cursor-paginated (API_GUIDELINES names reviews explicitly) — chunk 6.

## Files changed on this branch

- New: `modules/reviews/src/main/resources/db/migration/reviews/V4.1__create_review_tables.sql`
- New: `app/src/test/java/com/example/geohousing/app/reviews/ReviewsMigrationIntegrationTest.java`
- New: `docs/plans/005-reviews-module.md`
- Edit: `modules/reviews/build.gradle.kts`, `docs/plans/004-properties-module.md` (closed),
  `docs/handoffs/current-task.md`

No production Java yet; nothing consumes the module.

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:reviews:check` — passed.
- `./gradlew :app:test` — passed, **75 app tests, 0 failures**. `ReviewsMigrationIntegrationTest` (8)
  covers the migration applying, the tables existing, and every constraint biting: unknown
  relationship/status, an inverted residence period, `PUBLISHED` without `published_at`, the
  one-live-review index (rejected while live, allowed after `REMOVED`), duplicate version numbers,
  blank body, unknown recommendation, and the category-rating rules.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).
- `V4.1` was exercised directly against dev Postgres first, constraint by constraint, before the test
  was written.

## Known failures

None.

## Risks and unresolved questions

- The relationship vocabulary and the one-live-review rule are product decisions; both are cheap to
  change now via a migration and expensive once reviews exist in production.
- `recommendation` is `NOT NULL`: a structured review must state one. If that turns out to be too
  strict for a "notes only" review, it needs a migration to relax.
- Chunk 4 is the first cross-module call in the codebase; how `properties.api` is shaped (and how
  reviews behaves when a property is missing/merged/hidden) is the main open design question.

## Human actions required

Review and merge `feat/005-reviews-chunk1-foundation` after a fresh independent review (implemented by
Claude Code; the review must be a fresh independent pass). The branch is local and not pushed; there is
no credential path to push from this environment.

## Recommended next action

Independent review of chunk 1, then merge to `main`. Chunk 2 (the `Review` aggregate and its state
machine) branches from `main` after that; it is domain-only, so it does not need a fresh plan-mode
pass.

## Last updated

2026-07-20

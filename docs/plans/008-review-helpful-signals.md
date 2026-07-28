# Review Helpful Signals: Abuse-Resistant Ranking Input

Status: Active
Owner: Codex
Related issue: none (continues plan 005, chunk 8)
Last updated: 2026-07-28

## Objective

Add an auditable, privacy-preserving positive helpfulness signal for published reviews. It is a
ranking input only; it neither changes a review's verification level nor exposes voter identities or
an exact organic-ranking formula.

## Acceptance criteria

- [x] The `reviews` schema owns an append-only-safe helpful-signal table in the `V4.x` namespace;
      its voter and review references remain local/opaque as appropriate, and it prevents more than
      one active positive signal from the same account for the same review.
- [x] A signed-in account can add and withdraw its signal only on a published review and never on
      its own review; invalid, duplicate, and unauthorized paths have explicit tests.
- [x] Public review representations expose only an aggregate helpful count, never voter identities,
      timestamps, or ranking inputs.
- [x] Persistence keeps the aggregate consistent under concurrent signals and has integration tests
      for unique-constraint races.
- [ ] The ranking layer consumes a versioned, bounded helpfulness input without publishing its
      formula; paid status never affects this input or ordering.

## Non-goals

- Down-votes, reactions, comments, badges, contributor reputation, or social graph features.
- A full ranking algorithm, ranking API, or public explanation of ranking weights.
- Cross-module fraud detection, IP/device fingerprinting, or a new rate-limiting service.
- Moderation reports, appeals, and representative replies (the future moderation module).

## Current system

`reviews` already owns review publication state, immutable versions, and review moderation audit
under `V4.1`–`V4.3`. Published reviews are publicly readable while all mutations require an
authenticated account. Plan 005 implemented chunks 1–7; its optional chunk 8 is continued here.
The PRD and `DOMAIN_MODEL.md` list helpful signals as a ranking input, and `TRUST_VERIFICATION.md`
requires verification to remain only one non-dominant input.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Signal type | One positive helpful signal, removable by its voter | Meets the MVP should-have without turning reviews into a polarising reaction system | Product research supports additional reactions |
| Exposure | Aggregate count only | Voter identities and timing enable targeting/campaign analysis | A privacy-reviewed reputation feature is planned |
| Eligibility | Published review, non-author voter | Prevents draft probing and trivial self-promotion | Moderation policy adds exceptions |
| Ranking | Versioned bounded input, formula private | PRD requires auditability but forbids a manipulable exact formula | Ranking module is explicitly planned |

## Implementation chunks

1. **Foundation + schema** (this branch): `V4.4` helpful-signal table and migration integration
   coverage. The database enforces one active signal per voter/review; no endpoint or ranking
   behaviour is added.
2. **Domain + application**: signal aggregate/value types, eligibility checks, repository port and
   in-memory tests for author, state, and duplicate negative paths.
3. **Persistence + query projection**: JPA adapter, transactional count projection, and concurrent
   signal integration coverage.
4. **HTTP contract**: add/withdraw endpoints and a public-safe aggregate count to review responses,
   with authorization and privacy tests.
5. **Ranking input**: a versioned bounded input contract and audit metadata, without changing a
   public sort order until ranking policy is approved.

## Verification

```bash
cd apps/api
./gradlew :app:test --tests 'com.example.geohousing.app.reviews.ReviewsMigrationIntegrationTest'
./gradlew :modules:reviews:check
cd .. && ./scripts/check.sh
```

## Risks and rollback/forward-fix

Signals can be brigaded even with the unique voter/review constraint. This first slice deliberately
stores no network/device identifiers; any stronger anti-abuse controls require a separate privacy
and policy decision. The migration is append-only; a forward fix may disable writes but must retain
records needed to explain historical aggregates.

## Progress log

- 2026-07-27: Plan created from plan 005 chunk 8. Started chunk 1 on
  `feat/008-review-helpful-signals-foundation` from clean `main` at `5bd6e39`.
- 2026-07-27: Chunk 1 implemented. `V4.4` creates `reviews.review_helpful_signal` with a local
  `review_id` foreign key, opaque voter UUID, preserved `withdrawn_at`, and a partial unique index
  limiting one active signal per voter/review. Focused Postgres integration tests prove Flyway
  application, local-reference integrity, duplicate-active rejection, and re-signalling after
  withdrawal. `:modules:reviews:check` and `./scripts/check.sh` pass. Ready for independent review.
- 2026-07-27: Chunk 1 fast-forward merged to `main` at `b6a20d7`.
- 2026-07-27: Chunk 2 implemented on `feat/008-review-helpful-signals-application`. Added the
  private `HelpfulSignal` lifecycle and opaque signal/voter identifiers, application port and
  service, plus in-memory tests. A signal may be added only to a published, non-author review;
  unpublished targets are reported as missing to avoid review-state probing, self-signals are
  refused, duplicate active signals conflict, and withdrawal is idempotent. No persistence adapter,
  endpoint, aggregate count, or ranking behaviour was added. Focused tests,
  `:modules:reviews:check`, and `./scripts/check.sh` pass. Ready for independent review.
- 2026-07-27: Chunk 2 fast-forward merged to `main` at `e2665b4`.
- 2026-07-27: Chunk 3 implemented on `feat/008-review-helpful-signals-persistence`. JPA now maps
  `V4.4` signals and translates only the named active-voter partial-unique index into the existing
  application conflict. The active total is derived from the rows rather than stored separately, so
  concurrent writes cannot drift a counter; `HelpfulSignalQueryService` exposes only the count for
  a published review. A real Postgres test runs two simultaneous creates and proves exactly one
  succeeds, the other becomes the domain conflict, and the active count remains one. Focused unit
  and integration tests, `:modules:reviews:check`, and `./scripts/check.sh` pass. Ready for
  independent review.
- 2026-07-27: Chunk 3 fast-forward merged to `main` at `5ac585d`.
- 2026-07-28: Chunk 4 implemented on `feat/008-review-helpful-signals-http` from clean `main` at
  `803a664`. `POST`/`DELETE /api/reviews/{id}/helpful` add and withdraw the caller's signal and
  answer with the review's new aggregate only; the response deliberately omits whether the caller
  currently has an active signal. Review and listing representations carry `helpfulCount`. The
  listing resolves counts for a whole page in one grouped query, so the anonymous read path does not
  degrade as a property accumulates reviews. `ReviewResponse.from` now always requires the count, so
  a path that forgets it fails to compile instead of silently reporting zero. Self-signals are
  `403 HELPFUL_SIGNAL_SELF_NOT_ALLOWED`, duplicates `409 HELPFUL_SIGNAL_ALREADY_ACTIVE`, and an
  unpublished target is `404 REVIEW_NOT_FOUND` so the endpoint cannot probe the moderation queue.
  Nine endpoint integration tests cover anonymous write refusal, anonymous count reads, add and
  idempotent withdraw, duplicate conflict, self-signal refusal, unpublished and missing targets,
  per-review counts in a listing, and the absence of any voter identity in every response body.
  `:modules:reviews:check` and `./scripts/check.sh` pass. Ready for independent review.

## Final outcome

Not yet complete.

# Task handoff

## Objective

Implement plan 008, chunk 5: turn the helpful-signal count into a versioned, bounded ranking input a
future ranking layer can consume, with audit metadata, without changing any public sort order. This
is the last chunk of plan 008.

## Active branch

`feat/008-review-helpful-signals-ranking`, branched from clean `main` at `5d91652`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/008-review-helpful-signals.md`, chunk 5 of 5 (plan now marked Complete).

## Current status

completed, awaiting independent review

## Completed work

- `RankingInputVersion` (currently `V1`, with `current()`) — the version a value was produced under.
- `HelpfulnessInput` — record `(value, version)` whose compact constructor rejects anything outside
  `[0, 1]`, `NaN`, or a null version, so boundedness is a type invariant.
- `HelpfulnessInputPolicy` — pure, versioned, saturating transform from active-signal count to input.
  `forActiveSignals(count)` uses the current version; `forActiveSignals(count, version)` replays a
  named one for audit.
- `ReviewRankingInputService` — batch inputs for reviews the caller has already been authorised to
  see, reusing `HelpfulSignalQueryService.activeCountsForVisibleReviews` from chunk 4. Returns an
  entry for every requested review; unsignalled ones score zero rather than being absent.
- Wired in `ReviewsBeanConfiguration`, so the booting `@SpringBootTest` proves it constructs.
- `docs/DECISION_LOG.md` gained `P-012`; plan 008 is closed at 5/5.

## Remaining work

None for chunk 5, and none for plan 008. Applying the input to an actual sort order is deliberately
out of scope: it needs approved ranking policy and the other PRD §6 factors (completeness, recency
decay, diversity, moderation confidence), none of which exist yet.

## Decisions made

Founder decisions taken 2026-07-28, recorded as `P-012`:

- **Bounded and saturating, not raw.** An unbounded count would let a large enough voting cohort
  outweigh every other ranking factor, which PRD §6 and TRUST_VERIFICATION §5 both forbid. Past the
  saturation threshold further signals buy nothing, so a campaign of 10,000 wins what a modest one
  already won.
- **Derived, not stored.** No migration. The signal rows are append-only and preserve `withdrawn_at`,
  so replaying a historical count through a named version reproduces the exact input used then. A
  stored column would only add something that can drift from the rows it summarises.
- **Internal to reviews, not a published contract.** `search` and `analytics` are still empty
  `package-info` shells, so a `reviews.api` type would have no consumer — the same reasoning that
  deferred `properties.api` until plan 005 chunk 4 gave it one. ARCHITECTURE §5 already assigns
  ranking inputs to this module.
- **Never exposed publicly.** `helpfulCount` stays the only public number. Publishing the derived
  value would let anyone recover the curve by adding a signal and watching it move.
- **Purity as the paid-status guarantee.** The policy takes a count and a version and nothing else,
  so sponsorship cannot influence a calculation it is not an argument to. A test pins the signature.

## Assumptions

- A future ranking layer will weigh this against other inputs; it is not a rank by itself, and the
  `[0, 1]` scale is the contract that lets it be weighted without knowing the curve.

## Files changed

- new `modules/reviews/.../domain/RankingInputVersion.java`, `HelpfulnessInput.java`,
  `HelpfulnessInputPolicy.java`
- new `modules/reviews/.../application/ReviewRankingInputService.java`
- `modules/reviews/.../infrastructure/ReviewsBeanConfiguration.java`
- new `modules/reviews/src/test/.../domain/HelpfulnessInputPolicyTest.java`,
  `HelpfulnessInputTest.java`, `.../application/ReviewRankingInputServiceTest.java`
- `docs/DECISION_LOG.md`, `docs/plans/008-review-helpful-signals.md`,
  `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:reviews:test --tests 'com.example.geohousing.reviews.domain.Helpfulness*' --tests 'com.example.geohousing.reviews.application.ReviewRankingInputServiceTest'`
- `cd apps/api && ./gradlew :modules:reviews:spotlessApply`
- `cd apps/api && ./gradlew :modules:reviews:check :app:test --tests 'com.example.geohousing.app.reviews.ReviewEndpointIntegrationTest' --tests 'com.example.geohousing.app.architecture.*'`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. 20 new tests: 10 policy invariants (bounded for every count including
`Long.MAX_VALUE`; zero signals score zero; monotonic; each additional signal worth no more than the
last; 10,000 signals score exactly what 25 do; negative count rejected; value depends on the count
alone; version carried; a named version replays), 5 on the `HelpfulnessInput` bound and version, and
5 on the service. The ArchUnit boundary rules and `ReviewEndpointIntegrationTest` (which asserts the
public listing order) both still pass, confirming no boundary moved and no ordering changed.

## Known failures

None observed.

## Risks and unresolved questions

- The saturation threshold is a judgement call, not a measured value — there is no production data
  yet. Retuning it means adding `RankingInputVersion.V2` rather than editing `V1`, which is the
  discipline the version exists to enforce.
- Coordinated voting below the saturation threshold is still worth something. Bounding limits the
  ceiling; it is not fraud detection, which remains separate policy work.

## Human actions required

None.

## Recommended next action

Independent review of the chunk-5 branch in a fresh session, then merge. Plan 008 is then closed;
the next task should start from a new plan.

## Last updated

2026-07-28

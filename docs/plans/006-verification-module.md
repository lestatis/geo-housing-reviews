# Verification Module: Relationship Verification Cases, Decisions, Tier Projection

Status: Active
Owner: Claude Code
Related issue: none (direct founder request; follows the reviews module, plan 005)
Last updated: 2026-07-23

## Objective

Implement the `verification` backend module: a private workflow that answers **"do we have evidence
that this account had the claimed relationship with this property?"** — never "is the review true?"
(TRUST_VERIFICATION.md §1). A decision produces a strength **tier** and a public-safe **badge**; the
tier feeds the `VerificationTier` projection the `reviews` aggregate already carries. This is MVP
loop 3 ("Verify relationship → improve trust signal").

## Acceptance criteria

- [ ] `verification` schema owns its tables under `V5.x`; `account_id`/`property_id` are opaque — no
      cross-module foreign keys.
- [ ] A `VerificationCase` aggregate with a state machine (PENDING → APPROVED / REJECTED / EXPIRED /
      CANCELLED), a relationship claim, a method, a decision reason code and a policy version.
- [ ] Strength is a **tier**, never a bare `verified=true`: UNVERIFIED / RELATIONSHIP_SIGNAL /
      DOCUMENT_VERIFIED (TRUST_VERIFICATION.md §2). Tier 0 content still publishes.
- [ ] A public-safe `VerificationBadge` projection: type, verified-at, valid-through, explanation
      key — never a document/apartment number or exact private address.
- [ ] On a decision, the tier is pushed to the `reviews` module through a published contract; a
      review submitted later picks up an existing badge. Reviews never reads verification tables and
      verification never reads reviews tables.
- [ ] The property reference is validated through `properties.api` — no direct table access.
- [ ] Every decision and every evidence access (when Tier 2 lands) is audited. Automated signals
      prioritise cases; they never make an adverse decision without a human (TRUST_VERIFICATION.md
      §7, SECURITY_PRIVACY.md §2).
- [ ] Badges can be revoked and can expire (current → former resident); revocation reverts the
      review to unverified without deleting it (TRUST_VERIFICATION.md §8).

## Non-goals

Ranking algorithms (TRUST_VERIFICATION.md §5 — a separate module; this plan only produces the trust
signal, it does not weight it); Tier 3 registry/partner integration (§3, needs legal/API validation);
representative-claim verification (a separate claim type); machine truth-scoring of allegations
(SECURITY_PRIVACY.md §8 — explicitly out of MVP).

## The Tier 2 evidence decision (scope boundary)

P-004 accepts shipping Tier 1 and Tier 2 together at launch. They are built in sequence, not because
Tier 2 is cut, but because the two are very unequal:

- **Tier 1 (relationship signal)** — invitation from a verified resident, a building one-time code, a
  consented location signal. **No raw evidence is stored.** The case / decision / badge machinery is
  self-contained.
- **Tier 2 (document-assisted)** — lease fragment, utility bill, ownership extract. This is **highly
  sensitive data** (SECURITY_PRIVACY.md §1) requiring private quarantine storage, encryption,
  object-level authorization, short-lived signed URLs, a retention deadline set at upload, an
  automatic deletion job with audit, and evidence-access auditing (TRUST_VERIFICATION.md §6). None of
  that storage substrate exists yet — it is essentially the `media` module.

**Decision:** build the shared case / decision / badge / tier-projection spine with **Tier 1 first**
(this plan, chunks 1–7), and deliver **Tier 2 evidence as plan 007** — the same spine, adding the
evidence subsystem under its own security review. This honours P-004's intent (both tiers before
public launch) without folding a highly-sensitive storage build into a state-machine chunk. Recorded
as scheduled, not dropped — the same treatment properties chunk 8 received.

## Module-wide decisions

| Decision | Choice | Reason |
|---|---|---|
| Migration namespace | `verification` schema, `V5.x` (root=1, identity=2, properties=3, reviews=4, verification=5) | Module owns its tables |
| Cross-module refs | `account_id` / `property_id` are opaque UUIDs, no FKs; property checked via `properties.api` | No module reads another module's tables |
| Strength model | A `VerificationTier` enum aligned with the reviews projection, derived from the method — never a bare boolean | TRUST_VERIFICATION.md §2 forbids `verified=true` |
| Relationship claim | CURRENT_RESIDENT / FORMER_RESIDENT / OWNER / FORMER_OWNER — the badge vocabulary of §4 | Matches the public badge types; broader than reviews' RelationshipType by design (owners verify, then review) |
| Verification → reviews | Reviews publishes an **inbound** `reviews.api` port; verification **pushes** the tier on a decision, and a review **pulls** the current badge at submit time | Reviews owns the projection (DOMAIN_MODEL Review `verification_summary`); push keeps existing reviews fresh, pull covers verify-then-review |
| Decisions are audited | Append-only `verification_decision_audit_event`, reason code mandatory | SECURITY_PRIVACY.md §4; mirrors reviews V4.3 / properties V3.3 |
| Automated signals | Record fraud signals on the case; decisions are always made by a human moderator | TRUST_VERIFICATION.md §7: prioritise, do not auto-decide adversely |
| Badge safety | Public projection carries no document/apartment number or exact address; includes the mandated tooltip explanation key | DOMAIN_MODEL VerificationBadge; TRUST_VERIFICATION.md §4 |

## Chunk breakdown (one branch each: self-check → fresh independent review → merge before next)

1. **Foundation + schema** (this chunk): module build deps, `V5.1` (`verification_case` +
   `verification_decision_audit_event`), migration test, this plan.
2. **Domain**: `VerificationCase` aggregate + state machine, `VerificationMethod`,
   `RelationshipClaim`, `VerificationTier`, `VerificationBadge` projection, tier-from-method mapping;
   activates the verification ArchUnit domain-purity rules.
3. **Application**: repository port, a `PropertyLookup`-style check via `properties.api`, an
   open-case service and a decision service (approve/reject/expire/cancel) with audit; in-memory-fake
   unit tests.
4. **`reviews.api` inbound port + tier projection**: reviews publishes the minimal contract for
   applying a verification tier to an author's review of a property; verification's adapter calls it
   on a decision, and reviews pulls the current badge at submit. The first inbound api reviews owns.
5. **Persistence adapters**: JPA for the case/decision graph; integration tests.
6. **Public + admin endpoints**: a user opens a Tier 1 case and reads their own case/badge; an admin
   verification queue approves/rejects; RFC 7807 handler scoped to verification.
7. **Revocation + expiration**: revoke a badge (reverts the review to unverified, does not delete it),
   expire current-resident badges to former-resident; audit throughout.
8. **Tier 2 evidence** — deferred to **plan 007** (quarantine storage, encryption, signed URLs,
   retention + deletion job, evidence-access audit).

## Chunk 1 — foundation + schema (this chunk)

- `modules/verification/build.gradle.kts`: Spring Boot BOM + `spring-boot-starter-data-jpa` +
  `properties` (for the future `properties.api` check) + `assertj`; web deferred to the endpoints
  chunk, matching how reviews started.
- `V5.1__create_verification_tables.sql` under the verification migration path:
  - `verification.verification_case` — opaque `account_id`/`property_id` (no cross-module FK),
    `relationship_claim` + `method` + `status` CHECKs, `tier` defaulting to `UNVERIFIED`,
    `decision_reason_code`, `policy_version`, `verified_at`, `valid_through`, `decided_by`,
    timestamps, `version BIGINT`. Partial unique index: at most one non-terminal case per
    (account, property).
  - `verification.verification_decision_audit_event` — append-only, no FK on the actor/target
    columns (identity's account; a case that does not exist must still be auditable), reason code
    mandatory, action + outcome CHECKs, mirroring reviews `V4.3`.
- `app/.../verification/VerificationMigrationIntegrationTest.java`: `V5.1` applied; tables exist; the
  claim/method/status/tier CHECKs reject bad values; the one-live-case index bites; the audit reason
  code is mandatory.
- Verify the migration against dev Postgres constraint-by-constraint **before** writing the test.

No production Java in chunk 1 beyond the migration; nothing consumes the module yet.

## Verification (chunk 1)

```bash
cd apps/api
./gradlew :modules:verification:check
./gradlew :app:test          # VerificationMigrationIntegrationTest applies V5.1 on Testcontainers Postgres
cd .. && ./scripts/check.sh
```

## Progress log

- 2026-07-23: Plan approved (Tier 1 spine now, Tier 2 evidence deferred to plan 007; verification →
  reviews as push-on-decision plus pull-at-submit). Chunk 1 begins.
- 2026-07-23: Chunk 1 (foundation + schema) merged: `V5.1` (case + decision audit), build deps, 7
  migration integration tests.
- 2026-07-23: Chunk 2 (domain) implemented on `feat/006-verification-chunk2-domain`. Framework-free
  `verification.domain`: opaque `AccountRef`/`PropertyRef`/`ModeratorId`, `RelationshipClaim`,
  `VerificationMethod` (each method carries the tier it grants — strength is a property of the
  method, not a moderator's free choice, §2), `VerificationTier` (own type, same values as reviews'),
  `VerificationStatus` (with `isLive`/`isTerminal`), `VerificationBadgeType` + `VerificationBadge`,
  and the `VerificationCase` aggregate. State machine: PENDING → APPROVED (grants the method's tier,
  records verified-at/decider/reason) / REJECTED / CANCELLED (user, no decider); APPROVED → EXPIRED
  (system, no moderator) or revoke → REJECTED (reverts tier to UNVERIFIED, never deletes — the review
  falls back to unverified per §8). Decision worth review: **the badge label depends on the tier, not
  just the claim** — a Tier 1 signal is always `RELATIONSHIP_SIGNAL_CONFIRMED`, never
  "verified current tenant"; only Tier 2 earns the four claim-specific badges (§2). Badge carries no
  document/apartment/address, only a localisation key for the mandated tooltip. **Bug caught by a
  test and fixed:** the decision methods mutated `status` before validating the reason code, so a
  blank reason left the aggregate half-transitioned; now every decision validates before touching
  state (a regression test asserts a rejected reason leaves the case cleanly PENDING). Invariants
  mirror `V5.1` (approved ⇒ verified-at; decided ⇒ decider+reason; approved ⇒ a granted tier).
  ArchUnit domain-purity now binds real `verification.domain` code. 18 unit tests;
  `./scripts/check.sh` passes.

- 2026-07-23: Chunk 3 (application layer) implemented on `feat/006-verification-chunk3-application`.
  Ports: `VerificationCaseRepository` (by id; the live case for an account+property mirroring the
  V5.1 partial index; latest case for reading a badge; a status queue; create/save), the
  `PropertyLookup` outbound port (resolve a property to its merge survivor — verification does *not*
  ask about reviewability, since one may verify a past relationship to a now-withheld property), and
  `VerificationDecisionRepository` (applyDecision + recordAttempt, committing the mutation and its
  audit row together). Domain audit types added: `VerificationDecisionAction`,
  `VerificationDecisionOutcome`, `VerificationDecisionAuditEvent` (actor absent only for system
  EXPIRE; reason mandatory), plus `VerificationCaseNotFoundException` /
  `VerificationVersionConflictException`. Services: `VerificationSubmissionService.open` (property
  resolved to survivor; one live case per account+property, refused with the existing id) and
  `.cancel` (owner-only; a moderator withdrawing a case is a REJECT, which is audited);
  `VerificationDecisionService.approve` / `.reject` (reason validated first, stale expectedVersion →
  409 before the transition, missing case → audited NOT_FOUND + empty); `VerificationQueryService`
  (getById with a viewer, findMine, moderators-only pending queue). Decision worth review:
  **visibility is one shared rule** — a case is visible only to its owner and moderators, and reported
  as *not found* to anyone else (a private workflow must never confirm that a given account is
  verifying a given property), the same 404-not-403 stance as reviews but with **no anonymous
  viewer** (a case is confidential, whereas a published review is public). 21 application unit tests
  against in-memory fakes (39 in the module), including the negative authorization paths;
  `./scripts/check.sh` passes.

- 2026-07-23: Chunk 4 (reviews.api inbound port + tier projection) implemented on
  `feat/006-verification-chunk4-reviews-projection`. **Architecture decision, founder-approved:** the
  plan's "push-on-decision plus pull-at-submit" would make reviews and verification mutually
  dependent, which the no-cycles ArchUnit rule forbids. Resolved to **push-only, verification →
  reviews** (reviews never calls verification). Reviews publishes its **first inbound contract**:
  `reviews.api.ReviewVerificationUpdater` + `ReviewVerificationTier` (own enum, not the domain type —
  the properties.api discipline), implemented by `ReviewVerificationApplier` which sets the tier on
  the author's live review (reusing `findLiveByAuthorAndProperty` + `updateVerificationTier`).
  Verification gets a `ReviewProjection` outbound port, a `ReviewProjectionAdapter` over reviews.api,
  and a `CatalogPropertyResolver` over properties.api (renamed from CatalogPropertyLookup to avoid a
  component-scan bean-name clash with reviews' identically-named adapter — a real failure the app
  context caught). `VerificationDecisionService` now pushes the resulting tier after every decision
  (approve → RELATIONSHIP_SIGNAL, reject → UNVERIFIED; NOT_FOUND pushes nothing), as a separate
  idempotent concern from the decision's own transaction. **Accepted trade-off:** a review written
  *after* approval starts UNVERIFIED until a re-push — the verify-first gap, closed later. The
  verification *services* are not wired in the app yet (they need the chunk-5 repositories); the two
  outbound adapters wire standalone and the app context stays green. Reviews half proven end-to-end
  on Postgres (`ReviewVerificationProjectionIntegrationTest`); no-cycle ArchUnit confirmed green.
  7 new unit tests (reviews applier 4 + verification push assertions) + 3 integration; 98 module
  tests; `./scripts/check.sh` passes.

## Out of scope

Tier 2 evidence storage (plan 007); ranking weights and the ranking module; Tier 3 registry
integration; representative claims; automated adverse decisioning; machine translation of badge copy.

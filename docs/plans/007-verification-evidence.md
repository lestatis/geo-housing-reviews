# Verification Evidence: Tier 2 Document-Assisted Verification

Status: Active
Owner: Claude Code
Related issue: none (direct founder request; continues the verification module, plan 006)
Related ADR: `docs/adr/0008-verification-evidence-object-storage.md`
Last updated: 2026-07-23

## Objective

Add **Tier 2** verification to the `verification` module: an account attaches a document (lease
fragment, utility bill, ownership extract) to a pending case, a verification moderator reads it under
audit and decides, and the raw evidence is deleted shortly after the decision and appeal window,
leaving only decision metadata. This completes MVP must-have "safe evidence upload and retention
workflow" and honours P-004 (Tier 1 **and** Tier 2 at launch).

Plan 006 delivered the case/decision/badge spine and Tier 1; this plan adds the evidence subsystem on
top of it, under its own security review because the data is the most sensitive the platform holds.

## Acceptance criteria

- [ ] Evidence bytes live in S3-compatible object storage; PostgreSQL holds only metadata (key, type,
      size, checksum, timestamps, retention deadline) — never document content (ADR-0008).
- [ ] Objects are never publicly readable, keys are not guessable, and reads are short-lived and
      authorized; every moderator access to evidence is audited.
- [ ] A retention deadline is set **at upload**; a deletion job removes the object, stamps the
      metadata row, and records completion (SECURITY_PRIVACY §4 "deletion jobs with evidence of
      completion"). Retention periods are configuration, never hard-coded.
- [ ] The `DOCUMENT` method is added to the case `method` CHECK by a new migration and grants
      `DOCUMENT_VERIFIED`, which makes the four claim-specific badges reachable for the first time.
- [ ] An account may cancel before a decision, and cancelling deletes the evidence.
- [ ] Raw evidence never appears in logs, analytics, review APIs or support tooling.
- [ ] Uploads are validated (content-type allowlist, size cap) before anything is stored.
- [ ] Tests use synthetic fixtures that cannot be mistaken for real documents
      (`.claude/rules/security.md`).

## Non-goals

Public property media / the photo gallery (the `media` module, an MVP *Should-have*); application-level
envelope encryption (recorded as a pre-launch follow-up in ADR-0008); malware scanning and image
re-encoding (pre-launch requirement, see Risks); Tier 3 registry integration; the reason-code
taxonomy (moderation policy).

## Module-wide decisions

Recorded in full in **ADR-0008**; summarised here:

| Decision | Choice |
|---|---|
| Storage | S3-compatible object storage; MinIO for local dev and Testcontainers; AWS SDK v2 client |
| Metadata | Postgres `verification.verification_evidence` — key/type/size/checksum/retention only |
| Ownership | The verification module owns evidence; **not** routed through `media` (one use case today) |
| Encryption | Server-side, plus object-level authorization, short-lived signed reads, access audit |
| Upload path | Through the API, so the file is validated before storage; no presigned **write** URLs |
| Migrations | `verification` schema continues at `V5.x` |

## Chunk breakdown (one branch each: self-check → fresh independent review → merge before next)

1. **ADR-0008 + storage substrate** (this chunk): the ADR, this plan, an `EvidenceStore` port with its
   value objects, the S3/MinIO adapter, MinIO in local compose, and a Testcontainers integration test
   proving put/read/delete against a real S3 API.
2. **`V5.2` schema**: `verification_evidence` + `verification_evidence_access_event`; add `DOCUMENT`
   to the case `method` CHECK. Verified constraint-by-constraint on scratch Postgres first.
3. **Domain**: evidence value objects (id, storage key, checksum, retention deadline), the
   `DOCUMENT` method granting `DOCUMENT_VERIFIED`, and the case aggregate's evidence rules.
4. **Application**: attach-evidence use case (validated), moderator read-with-audit, and the retention
   sweep; in-memory-fake unit tests.
5. **Persistence + storage adapters**: JPA for evidence metadata and the access audit; integration
   tests against Postgres and Testcontainers MinIO together.
6. **Endpoints**: upload to one's own pending case, moderator short-lived read, Tier 2 decisions;
   RFC 7807 scoped to verification.
7. **Retention deletion + access audit**: delete on decision/cancel, the sweep with proof of
   completion, and tests asserting the object is *actually gone* from storage.

## Chunk 1 — ADR + storage substrate (this chunk)

- `docs/adr/0008-verification-evidence-object-storage.md` and this plan.
- `EvidenceStore` application port: `put`, `readOnce`, `delete`, `exists`, over an opaque storage key.
- Value objects: `EvidenceStorageKey` (unguessable, namespaced by case), `EvidenceContentType`
  (allowlist), stored-object metadata.
- S3/MinIO adapter in `verification.infrastructure.storage`, configured by properties (endpoint,
  bucket, credentials, region) — no credentials in the repository.
- MinIO added to `infra/docker/docker-compose.yml` for local development.
- A Testcontainers MinIO integration test proving the real round trip: put → read → delete → gone.

No evidence metadata table and no endpoints in chunk 1; nothing consumes the store yet.

## Verification (chunk 1)

```bash
cd apps/api
./gradlew :modules:verification:check
./gradlew :app:test --tests '*EvidenceStore*'
cd .. && ./scripts/check.sh
```

## Risks and pre-launch requirements

- **No malware scanning or image re-encoding in MVP.** SECURITY_PRIVACY §4 lists "upload quarantine
  and re-encoding". This plan validates content type and size and quarantines the object, but does not
  scan or re-encode. **Recorded as a pre-launch requirement**, not a silent omission.
- **Envelope encryption** is a pre-launch follow-up (ADR-0008).
- **Retention periods need legal review**, which SECURITY_PRIVACY already flags as outstanding; they
  stay configurable so a legal answer changes configuration, not code.
- **Two consistency domains**: a metadata row and its object can diverge. Deletion must be idempotent,
  and an orphaned object must remain sweepable.

## Progress log

- 2026-07-23: Chunk 3 (domain) implemented on `feat/007-evidence-chunk3-domain`. `VerificationMethod`
  gains `DOCUMENT`, which grants `DOCUMENT_VERIFIED` and is the one method flagged `requiresEvidence`
  — so approving a Tier 2 case now produces one of the four claim-specific badges (VERIFIED_OWNER,
  etc.) for the first time, while the three signal methods keep the cautious RELATIONSHIP_SIGNAL
  label. `EvidenceId` and the `VerificationEvidence` entity: metadata only (storage reference,
  content type, size, hex `sha256`, retention deadline, upload/deletion times) mirroring the V5.2
  CHECKs; `markDeleted` is idempotent (a retryable sweep keeps the original deletion time) and
  `isPastRetention` is false once deleted, so a deleted row is never re-selected. The domain holds
  the storage reference as an opaque string — minting/validating the key format is a storage concern
  (application), so the domain only requires it present. `VerificationCase.acceptsEvidence()` = a
  PENDING, document-method case only. **Two chunk-2 assumptions inverted by design:** the badge test
  that asserted *every* method grants RELATIONSHIP_SIGNAL now distinguishes DOCUMENT → Tier 2. 12 new
  unit tests (58 in the module); `./scripts/check.sh` passes.
- 2026-07-23: Chunk 2 (`V5.2` schema) implemented on `feat/007-evidence-chunk2-schema`. Verified
  constraint-by-constraint on scratch Postgres before the test. `verification_evidence` holds
  metadata only — `storage_key` (unique), `content_type`, `size_bytes` (> 0), `sha256` (a hex CHECK),
  `retention_deadline` (set at upload), `uploaded_at`, and a nullable `deleted_at` that the retention
  sweep stamps so the metadata row survives as proof of deletion. `case_id` is a **real foreign key**
  (same module; a case is never deleted and metadata outlives the object, so it always holds).
  `verification_evidence_access_event` is append-only with `action IN (READ, DELETE)`; a moderator
  READ must record its accessor, a system DELETE (retention) may omit it — the same shape as the
  decision audit's EXPIRE. `DOCUMENT` joins the case `method` CHECK: because V5.1 defined that check
  inline (auto-named `verification_case_method_check`), the append-only way to extend it is DROP then
  ADD, done here. This makes the four claim-specific Tier 2 badges reachable for the first time. The
  chunk-1 migration test asserted DOCUMENT was *rejected*; that assertion is now inverted and V5.2
  coverage added. 10 migration integration tests; `./scripts/check.sh` passes.
- 2026-07-23: Plan approved with two founder decisions — S3-compatible object storage (over Postgres
  `bytea`, because evidence must not enter database backups if retention is to mean anything) and
  server-side encryption (with envelope encryption recorded as a pre-launch follow-up). Chunk 1
  begins.

## Out of scope

Public media/photo gallery; ranking; Tier 3 registry integration; representative-claim verification;
moderation reports and appeals.

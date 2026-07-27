# Task handoff

## Objective

Add **Tier 2 document evidence** to the `verification` module (plan `docs/plans/007-verification-evidence.md`):
an account attaches a document to a pending case, a moderator reads it under audit and decides, and the
raw evidence is deleted shortly after the decision. Completes MVP must-have "safe evidence upload and
retention workflow" and P-004 (Tier 1 + Tier 2 at launch). This is chunk 4 (application).

## Active branch

`feat/007-evidence-chunk4-application` (branched from `main` at `7d2b821`)

## Related issue or plan

No issue. See `docs/plans/007-verification-evidence.md` and `docs/adr/0008-verification-evidence-object-storage.md`.
This is chunk 4 of 7.

## Current status

chunk4_implemented — ready for fresh independent review and merge before chunk 5.

### Evidence chunk 4 — application (this branch)

- Ports: `EvidenceRepository` (metadata) and `EvidenceAccessAuditRepository` (separate, because the
  audit is written on a read path). Domain gained `EvidenceAccessEvent`.
- `EvidenceRetentionPolicy` — deadline computed at upload, periods are configuration.
- `EvidenceService`: attach / read / listForCase / deleteForCase.
- `EvidenceRetentionService.deleteLapsed` — object deleted *before* the row is stamped.

**Points a reviewer should push on:**
- **The owner cannot read their own evidence back** (moderators only, §6). Deliberate; challenge if
  the product wants an owner preview.
- **A moderator cannot upload into a case they will judge** — separation of who supplies and who
  assesses.
- A non-moderator read is **404 with no audit row** (nothing disclosed); deleted evidence is a
  distinct `EvidenceContentGoneException`, not not-found.
- Sweep ordering: delete object → stamp row. An interruption leaves a re-sweepable row rather than a
  row claiming a deletion that never happened.

### Evidence chunk 3 — domain (merged to `main`)

- `VerificationMethod.DOCUMENT` grants `DOCUMENT_VERIFIED` and is `requiresEvidence`. Tier 2's four
  claim-specific badges are reachable through a real approval now.
- `VerificationEvidence` entity (metadata only) + `EvidenceId`; `markDeleted` idempotent,
  `isPastRetention` excludes deleted rows. Storage reference held as an opaque string.
- `VerificationCase.acceptsEvidence()` = PENDING + document method only.

**Points a reviewer should push on:**
- The domain holds the storage reference as a plain String, not the application's `EvidenceStorageKey`
  — key minting/validation is a storage concern, kept out of the domain.
- Evidence is a standalone entity (own repository in chunk 5), not nested in the case aggregate: it has
  an independent retention lifecycle and the access audit references it directly.
- The chunk-2 badge test asserting every method grants RELATIONSHIP_SIGNAL is deliberately updated.

### Evidence chunk 2 — V5.2 schema (merged to `main`)

- `verification_evidence` (metadata only — key/type/size/checksum/retention/deleted_at) and
  `verification_evidence_access_event` (append-only READ/DELETE audit). Verified on scratch Postgres
  first.
- `DOCUMENT` added to the case `method` CHECK (DROP + ADD, since V5.1's inline check was auto-named
  `verification_case_method_check`). Tier 2's four claim-specific badges are now reachable.

**Points a reviewer should push on:**
- `case_id` is a real FK (same-module, always holds because metadata outlives the object); evidence
  bytes are the thing that leaves, not the row.
- Access audit: a moderator READ records its accessor; a system DELETE may omit it (retention sweep).
- The chunk-1 assertion that DOCUMENT was rejected is deliberately inverted in this chunk.

### Evidence chunk 1 — ADR + storage substrate (merged to `main`)

- **ADR-0008** and **plan 007**, resolving the two founder decisions: S3-compatible object storage
  (over Postgres `bytea`, so evidence never enters DB backups and retention is enforceable) and
  server-side encryption (envelope encryption recorded as a pre-launch follow-up).
- `EvidenceStore` outbound port (`put`/`read`/`delete`/`exists`) with value objects: `EvidenceStorageKey`
  (namespaced by case, random suffix → unguessable), `EvidenceContentType` (allowlist), `StoredEvidence`.
- `S3EvidenceStore` adapter (the only class touching a storage SDK) — server-side AES256, no public ACL,
  size cap enforced by reading one byte past the limit, checksum of the bytes that landed. AWS SDK v2 S3
  dependency added via the version catalog.
- MinIO added to `infra/docker/docker-compose.yml`; app config defaults under
  `verification.evidence.storage` (throwaway localhost credentials only).
- Testcontainers MinIO integration test: put → read-back-exact → delete → **actually gone**, idempotent
  delete, oversized/empty refusal, and objects-are-not-public. Synthetic fixtures only.

**Points a reviewer should push on:**
- The MinIO test configures a local KMS key so SSE-S3 (AES256) works; managed S3 does AES256 natively.
  Confirm that is an acceptable test-only shim rather than a code smell.
- Upload is **through the API** (not presigned PUT) so bytes are validated before storage — ADR-0008.
- **No malware scanning / re-encoding** in MVP (content-type allowlist + size cap + quarantine only) —
  recorded as a pre-launch requirement in ADR-0008 and plan 007, not silently skipped.
- The app S3 client bean builds lazily, so existing Postgres-only `@SpringBootTest`s still boot without
  MinIO (confirmed).

## Remaining work

Chunks 5–7 (see the plan). Next: chunk 5 (persistence — JPA for evidence metadata and the access
audit; integration tests against Postgres and Testcontainers MinIO together).

## Decisions and assumptions

- Verification **owns** its evidence storage; not routed through a `media` module (one use case today).
- Postgres holds only evidence metadata; bytes live in the bucket.
- Retention periods stay configuration, never hard-coded (legal review pending).

## Commands and tests

```bash
cd apps/api
./gradlew :modules:verification:test --tests '*S3EvidenceStore*'   # Testcontainers MinIO
cd .. && ./scripts/check.sh
```

## Failures / unresolved risks

None. `./scripts/check.sh` passes. Two consistency domains (metadata rows vs objects) are a known
design property handled from chunk 2 on: deletion is idempotent and an orphaned object stays sweepable.

Environment note: scratch `geo-*-verify` containers may linger in `docker ps` because `docker stop/kill`
returns "permission denied" for this user; harmless `--rm` containers cleared by a daemon restart.

## Next action

Fresh independent review of chunk 1, then merge to `main` before chunk 2.

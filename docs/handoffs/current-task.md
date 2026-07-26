# Task handoff

## Objective

Add **Tier 2 document evidence** to the `verification` module (plan `docs/plans/007-verification-evidence.md`):
an account attaches a document to a pending case, a moderator reads it under audit and decides, and the
raw evidence is deleted shortly after the decision. Completes MVP must-have "safe evidence upload and
retention workflow" and P-004 (Tier 1 + Tier 2 at launch). This is chunk 1 (ADR + storage substrate).

## Active branch

`feat/007-evidence-chunk1-storage-substrate` (branched from `main` at `020cd0a`)

## Related issue or plan

No issue. See `docs/plans/007-verification-evidence.md` and `docs/adr/0008-verification-evidence-object-storage.md`.
This is chunk 1 of 7.

## Current status

chunk1_implemented — ready for fresh independent review and merge before chunk 2.

### Evidence chunk 1 — ADR + storage substrate (this branch)

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

Chunks 2–7 (see the plan). Next: chunk 2 (`V5.2` — `verification_evidence` +
`verification_evidence_access_event` tables; add `DOCUMENT` to the case `method` CHECK).

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

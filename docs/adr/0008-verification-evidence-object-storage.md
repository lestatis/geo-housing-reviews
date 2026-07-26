# ADR-0008: Store verification evidence in S3-compatible object storage, owned by the verification module

Status: Accepted
Date: 2026-07-23
Deciders: Founders
Related: `docs/plans/007-verification-evidence.md`, `docs/TRUST_VERIFICATION.md` §6,
`docs/SECURITY_PRIVACY.md` §1/§4/§6, `docs/ARCHITECTURE.md` §2

## Context

Tier 2 verification is document-assisted: an account attaches a lease fragment, utility bill or
ownership extract to a verification case, a moderator reads it, and a decision is made
(`docs/TRUST_VERIFICATION.md` §3). P-004 accepts shipping Tier 1 and Tier 2 together at launch.

This evidence is **highly sensitive** (`docs/SECURITY_PRIVACY.md` §1): it must be isolated,
encrypted, access-controlled, audited, and retained for the shortest justified period. §6 further
requires that retention be honoured *including in backups* ("deletion propagation"), and
TRUST_VERIFICATION §6 requires private quarantine storage, access only for verification moderators,
no raw evidence in logs/analytics/support tools, and an automatic deletion job with audit.

No storage substrate exists in the repository today: `infra/docker/docker-compose.yml` runs Postgres
only, and no module declares a storage dependency.

## Decision drivers

- Retention must be genuinely enforceable, backups included (§6).
- Highly sensitive data must be isolated from public media paths (§1).
- Do not introduce a production dependency without explaining the trade-off (AGENTS.md §3.5).
- Do not build a cross-cutting abstraction without two concrete current use cases
  (`.claude/rules/architecture.md`).

## Decision

**1. Evidence bytes live in S3-compatible object storage**, not in PostgreSQL. Local development and
tests use MinIO (Docker Compose and Testcontainers); production uses any S3-compatible service. The
client is the AWS SDK v2 S3 client — a new production dependency, accepted here.

**2. Postgres stores only evidence *metadata*** — storage key, content type, size, checksum, upload
time, retention deadline, deletion time. Never the document bytes.

**3. The verification module owns its evidence storage.** It is not routed through the `media`
module.

**4. Encryption is server-side** (bucket/provider SSE), combined with object-level authorization,
short-lived signed reads, and an evidence-access audit. Objects are never publicly readable and keys
are not guessable.

**5. Uploads go through the API**, not via a presigned direct `PUT`.

## Considered options

### Storage backend

**Option A — S3-compatible object storage (chosen).** Evidence lives outside the database, with its
own lifecycle and deletion.

- Pros: the decisive one is **retention**. Evidence kept in Postgres would be copied into every
  database backup, and honouring a "delete raw evidence shortly after the decision" deadline would
  then require rewriting or expiring backups — which §6 demands but which is impractical to prove.
  Object storage gives evidence an independent lifecycle that can actually be deleted and audited.
  Also matches ARCHITECTURE §2, which already prescribes S3-compatible object storage, and keeps
  large binaries out of the transactional database.
- Cons: a new production dependency and a new local/test dependency (MinIO); a second consistency
  domain — a row can exist without its object, or vice versa, so the code must tolerate and reconcile
  that.

**Option B — PostgreSQL `bytea`.** Rejected: no new infrastructure and evidence would inherit the
database's access control and encryption, but every backup would then contain highly sensitive
documents, making the retention deadline effectively unenforceable. That is the opposite of what §6
requires, and it is the reason this option loses despite being simpler.

### Encryption depth

**Option A — server-side encryption (chosen).** Provider-managed SSE plus strict object-level
authorization, short-lived signed reads and an access audit.

- Pros: available immediately, no key-management system to build, and the practical threat (a leaked
  URL, a curious insider, a public bucket) is addressed by authorization and short-lived reads rather
  than by cipher choice.
- Cons: the storage provider can technically read the objects.

**Option B — application-level envelope encryption.** Rejected *for now*: encrypting bytes in the
application before upload is strictly stronger and matches "highly sensitive" most literally, but it
needs real key management (a KMS or managed key store) that does not exist in this project yet.
Recorded as a **pre-launch follow-up**, to be revisited with the legal review that
`docs/SECURITY_PRIVACY.md` already says is outstanding.

### Module ownership

**Option A — the verification module owns evidence (chosen).**

- Pros: highly sensitive data stays isolated from public media code paths, so no public-image handler
  can ever serve an evidence object by accident. It also respects the repository rule that a
  cross-cutting abstraction needs two concrete current use cases: evidence is the *only* storage
  consumer today (a property photo gallery is an MVP *Should-have*, unbuilt).
- Cons: when the media module arrives there will be two storage integrations, and a shared
  abstraction may need extracting then — with two real use cases to design it against, which is
  precisely when that extraction is justified.

**Option B — route evidence through a general `media` module.** Rejected: ARCHITECTURE §5 does assign
"upload sessions, storage keys, deletion lifecycle" to media, but building that module now would mean
designing a generic abstraction from a single use case, and mixing quarantined evidence with public
sanitized media in one component is exactly the coupling §1 warns against.

### Upload path

**Option A — upload through the API (chosen).** The file reaches the application, which validates it
(content-type allowlist, size cap) before anything is written to storage, and no presigned **write**
URL is ever issued.

**Option B — presigned direct `PUT` to the bucket.** Rejected for MVP: it scales better and avoids
proxying bytes, but it hands out a write credential and stores whatever the client sends before the
application can inspect it. At MVP volumes the proxying cost is irrelevant and the control is worth
more.

## Consequences

### Positive

- Retention is enforceable and provable: deleting the object is a real, auditable act, and evidence
  never enters a database backup.
- Evidence is structurally isolated from public media.
- Uploads are validated before storage; no presigned write URLs exist.
- Local development and tests exercise the real S3 API through MinIO rather than a stub.

### Trade-offs

- A new production dependency (AWS SDK v2 S3) and a new local/test service (MinIO).
- Two consistency domains: metadata rows and objects can diverge, so deletion must be idempotent and
  reconcilable, and an orphaned object must still be sweepable.
- Server-side encryption means the storage provider can technically read objects until envelope
  encryption lands.

### Follow-ups recorded, not silently skipped

- **Application-level envelope encryption** with real key management, before handling production
  documents at scale.
- **Malware scanning and image re-encoding** on upload (`docs/SECURITY_PRIVACY.md` §4 lists "upload
  quarantine and re-encoding"). MVP validates content type and size and quarantines the object, but
  does not scan or re-encode — a pre-launch requirement.
- **Retention periods stay configurable**, never hard-coded; the exact values await the legal review
  that SECURITY_PRIVACY already flags as outstanding.

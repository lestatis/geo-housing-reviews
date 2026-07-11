# ADR-0006: Store a keyed hash of the auth subject, never the raw value

Status: Accepted
Date: 2026-07-11
Deciders: Founders
Related: `docs/plans/002-identity-module.md`, ADR-0005 (OAuth2 Resource Server), P-003 (public anonymity)

## Context

Each account maps to an external OIDC subject (the JWT `sub` claim, e.g. `google-oauth2|1092...`). We look it up on every authenticated request to resolve the account, and — for account deletion (`DELETE /api/me`, plan Chunk 8) — we must recognise a previously-deleted subject on a future login so we can refuse to silently recreate the account ("no resurrection").

`docs/SECURITY_PRIVACY.md` §1 classifies authentication identifiers as **confidential personal data**. The external subject is directly correlatable back to the person at the IdP. Retaining the raw subject after a deletion request means a data-subject deletion did not remove a direct external identifier — a real privacy gap, surfaced by independent review of plan Chunk 2.

## Decision drivers

- Privacy by default (`docs/PRODUCT_VISION.md` principle 5): don't retain a raw external identity identifier, especially after deletion.
- The no-resurrection guarantee still needs a stable way to recognise "this subject was already deleted."
- Lookup by subject happens on every request, so the stored form must be deterministic (same subject → same stored value).

## Considered options

### Option A — keyed hash (HMAC), never store raw (chosen)

Store `HMAC-SHA256(subject, pepper)` as `identity.account.auth_subject_hash`. Login/provisioning hashes the incoming JWT subject and looks up by hash. The raw subject is never persisted. A closed account keeps only the non-reversible hash, which still supports the no-resurrection check.

- Pros: no raw identifier ever stored; deletion leaves nothing reversible; deterministic lookup; the server pepper prevents offline correlation of the hash against a guessed subject list.
- Cons: introduces a server-side secret (pepper) that must be managed and must never rotate without a migration plan (rotating it orphans every existing account's lookup); slightly more work in the provisioning/JWT paths.

### Option B — keep raw subject, document the retention

Rejected: keeps a reversible confidential identifier after deletion, arguably contradicting §1; the small implementation saving isn't worth the weaker privacy posture.

### Option C — scrub subject on close, drop no-resurrection

Rejected: strongest deletion but loses the anti-resurrection property the product wants (a deleted user would silently get a fresh account on next login).

## Decision

Persist only `HMAC-SHA256(subject, pepper)` hex (`CHAR(64)`) in `identity.account.auth_subject_hash`. The pepper is injected config (`IDENTITY_AUTH_SUBJECT_PEPPER`), never committed. The domain `Account` holds this as an opaque `authSubjectHash` string and knows nothing about hashing — the HMAC is computed in the application/infrastructure layer (the domain stays framework- and crypto-free, per the ArchUnit rules). `Account.close()` retains the hash intentionally.

## Consequences

### Positive

- No raw external identifier is ever stored; account deletion retains nothing reversible.
- No-resurrection still enforceable via the retained hash.
- Domain purity preserved: hashing lives at the boundary, not in `Account`.

### Negative / trade-offs

- A server pepper secret must exist in every environment; tests use a fixed test pepper.
- The pepper cannot be rotated without a re-hash migration (impossible without the raw subjects, which we deliberately don't keep) — so pepper rotation effectively means invalidating existing lookups. Documented here so it is a conscious constraint, not a surprise.

### Where each piece lands (plan Chunk mapping)

- **Domain (Chunk 2, done):** `Account.authSubjectHash` holds the hash; `close()` retains it. No crypto in the domain.
- **Schema (Chunk 4):** a fix-forward migration renames `account.auth_subject VARCHAR(255)` (created by V2.1, already merged) to `auth_subject_hash CHAR(64)`. Safe because no production data exists yet, but done as a new migration since V2.1 is merged and migrations are append-only after merge.
- **Application (Chunk 3):** an `AuthSubjectHasher` port; `AccountProvisioningService` hashes before lookup/insert.
- **Infrastructure (Chunk 5):** an HMAC implementation reading the pepper from config; the JWT converter hashes the incoming `sub` before resolving the account.

## Revisit when

- The chosen IdP guarantees a stable, non-correlatable pairwise subject that is itself not personal data (some providers offer pairwise/sectoral identifiers) — then plain storage might be reconsidered.
- A pepper-rotation requirement emerges (would need a dedicated migration strategy).

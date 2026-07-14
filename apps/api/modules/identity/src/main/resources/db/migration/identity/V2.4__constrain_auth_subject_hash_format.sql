-- Fix-forward for V2.3, which is merged and therefore append-only.
--
-- V2.3 renamed account.auth_subject to auth_subject_hash and retyped it to
-- CHAR(64) without validating the existing values. CHAR(64) blank-pads shorter
-- values, so any row written before V2.3 keeps its RAW external OIDC subject in
-- a column that now claims to hold a hash, and the rename reports success. That
-- is exactly the reversible confidential identifier ADR-0006 exists to remove:
-- Chunk 5 resolves accounts by HMAC and would never match such a row again,
-- leaving orphaned raw personal data behind it.
--
-- The CHECK below rejects anything that is not a 64-character lowercase hex
-- HMAC-SHA256 digest (the exact stored form ADR-0006 decided on). Postgres
-- validates existing rows when the constraint is added, so this migration fails
-- loudly on any database still holding a pre-V2.3 raw subject rather than
-- letting it masquerade as a hash. It also constrains Chunk 5's hasher: a
-- non-hex encoding (e.g. base64, 44 chars) can no longer be silently padded to
-- 64 characters and stored as if it were correct.
--
-- Forward fix if this migration fails: a raw subject cannot be converted into a
-- hash, and ADR-0006 records that no production account data exists, so the only
-- correct remediation is to drop the affected accounts and let them re-provision
-- on next login:
--
--   DELETE FROM identity.user_restriction WHERE account_id IN (
--       SELECT id FROM identity.account
--       WHERE auth_subject_hash !~ '^[0-9a-f]{64}$');
--   DELETE FROM identity.public_profile WHERE account_id IN (
--       SELECT id FROM identity.account
--       WHERE auth_subject_hash !~ '^[0-9a-f]{64}$');
--   DELETE FROM identity.account WHERE auth_subject_hash !~ '^[0-9a-f]{64}$';
--
-- Then re-run the migration.

-- V2.3 renamed the column but not its unique constraint, which Postgres had
-- auto-named after the old column. The persistence adapter matches this name to
-- translate a provisioning race into a domain exception, so it must say what it
-- actually constrains.
ALTER TABLE identity.account
    RENAME CONSTRAINT account_auth_subject_key TO account_auth_subject_hash_key;

ALTER TABLE identity.account
    ADD CONSTRAINT account_auth_subject_hash_format_check
    CHECK (auth_subject_hash ~ '^[0-9a-f]{64}$');

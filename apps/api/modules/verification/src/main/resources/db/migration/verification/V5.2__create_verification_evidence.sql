-- Tier 2 document evidence (docs/TRUST_VERIFICATION.md §3/§6; docs/adr/0008). The bytes live in
-- object storage; this table holds only metadata — key, type, size, checksum, retention deadline,
-- deletion time — never the document itself (SECURITY_PRIVACY.md §1/§5).
--
-- Tier 2 is now a real method, so DOCUMENT joins the verification_case method vocabulary. The V5.1
-- check was defined inline and auto-named verification_case_method_check; append-only means we drop
-- and re-add it rather than editing the applied migration.
ALTER TABLE verification.verification_case
    DROP CONSTRAINT verification_case_method_check;
ALTER TABLE verification.verification_case
    ADD CONSTRAINT verification_case_method_check
    CHECK (method IN ('INVITATION', 'BUILDING_CODE', 'LOCATION_SIGNAL', 'DOCUMENT'));

CREATE TABLE verification.verification_evidence (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Same-module reference, so a real foreign key. A case is never deleted, and evidence metadata
    -- outlives the object (deleted_at is stamped, the row stays), so the reference always holds.
    case_id            UUID        NOT NULL REFERENCES verification.verification_case(id),
    -- The object-storage locator. Unique so two rows can never claim the same object, and never a
    -- URL (ADR-0008): the application resolves and authorizes every read.
    storage_key        TEXT        NOT NULL UNIQUE,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT      NOT NULL CHECK (size_bytes > 0),
    -- Lowercase hex SHA-256 of the stored bytes, so a later read can be checked against what landed.
    sha256             CHAR(64)    NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- Set at upload (SECURITY_PRIVACY.md §6): the object must be deleted by this time.
    retention_deadline TIMESTAMPTZ NOT NULL,
    uploaded_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Null until the retention sweep deletes the object; the metadata row then remains as proof.
    deleted_at         TIMESTAMPTZ
);

-- A case's evidence, and the sweep's input: rows whose object is still present past its deadline.
CREATE INDEX verification_evidence_case_idx
    ON verification.verification_evidence (case_id);
CREATE INDEX verification_evidence_retention_idx
    ON verification.verification_evidence (retention_deadline)
    WHERE deleted_at IS NULL;

-- Append-only audit of every access to evidence (SECURITY_PRIVACY.md §4; TRUST_VERIFICATION.md §6:
-- "evidence access audit"). A moderator READ always records who; a system DELETE (retention sweep)
-- has no human actor, the same shape as the decision audit's EXPIRE.
CREATE TABLE verification.verification_evidence_access_event (
    id                  UUID        PRIMARY KEY,
    evidence_id         UUID        NOT NULL REFERENCES verification.verification_evidence(id),
    accessor_account_id UUID,
    action              VARCHAR(20) NOT NULL CHECK (action IN ('READ', 'DELETE')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT verification_evidence_access_read_has_accessor
        CHECK (action = 'DELETE' OR accessor_account_id IS NOT NULL)
);

CREATE INDEX verification_evidence_access_evidence_idx
    ON verification.verification_evidence_access_event (evidence_id, created_at);
CREATE INDEX verification_evidence_access_accessor_idx
    ON verification.verification_evidence_access_event (accessor_account_id, created_at);

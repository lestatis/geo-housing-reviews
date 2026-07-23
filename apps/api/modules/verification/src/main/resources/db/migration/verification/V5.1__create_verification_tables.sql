-- Verification module core tables (docs/DOMAIN_MODEL.md Verification context;
-- docs/TRUST_VERIFICATION.md). A verification case asks "do we have evidence that this account had
-- the claimed relationship with this property?" — never "is the review true?". A decision yields a
-- strength tier and a public-safe badge; the tier is projected onto the review by the reviews
-- module through a published contract (this module never touches reviews' tables).
--
-- account_id and property_id are opaque UUIDs, NOT foreign keys: accounts and properties belong to
-- other modules and no module references another module's tables (ARCHITECTURE boundary rules).
-- Property existence is checked through properties.api at the application layer.

CREATE SCHEMA IF NOT EXISTS verification;

CREATE TABLE verification.verification_case (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id           UUID        NOT NULL,
    property_id          UUID        NOT NULL,
    relationship_claim   VARCHAR(30) NOT NULL
                         CHECK (relationship_claim IN
                                ('CURRENT_RESIDENT', 'FORMER_RESIDENT', 'OWNER', 'FORMER_OWNER')),
    -- Tier 1 relationship-signal methods only. Tier 2 (DOCUMENT) arrives with the evidence
    -- subsystem in a later migration, keeping the accepted vocabulary explicit per chunk.
    method               VARCHAR(30) NOT NULL
                         CHECK (method IN ('INVITATION', 'BUILDING_CODE', 'LOCATION_SIGNAL')),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN
                                ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    -- Strength is a tier, never a bare verified=true (TRUST_VERIFICATION.md §2). Aligned with the
    -- reviews VerificationTier projection; Tier 1 methods resolve to RELATIONSHIP_SIGNAL.
    tier                 VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED'
                         CHECK (tier IN ('UNVERIFIED', 'RELATIONSHIP_SIGNAL', 'DOCUMENT_VERIFIED')),
    decision_reason_code VARCHAR(64),
    policy_version       INT         NOT NULL DEFAULT 1,
    verified_at          TIMESTAMPTZ,
    -- Current-resident badges may expire into "former resident" after a policy period (§8); null
    -- when no expiry applies.
    valid_through        TIMESTAMPTZ,
    decided_by           UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT      NOT NULL DEFAULT 0,
    -- An approved case is a live badge and must record when it was verified.
    CONSTRAINT verification_case_approved_has_verified_at
        CHECK (status <> 'APPROVED' OR verified_at IS NOT NULL),
    -- A moderator's decision (approve/reject) records who made it and why.
    CONSTRAINT verification_case_decided_has_decider
        CHECK (status NOT IN ('APPROVED', 'REJECTED')
               OR (decided_by IS NOT NULL AND decision_reason_code IS NOT NULL))
);

-- At most one live case per (account, property). PENDING and APPROVED both occupy the slot: an
-- approved badge blocks a duplicate, and rejection/expiry/cancellation frees it for a fresh attempt.
CREATE UNIQUE INDEX verification_case_one_live_per_account_property_idx
    ON verification.verification_case (account_id, property_id)
    WHERE status IN ('PENDING', 'APPROVED');

-- Read paths: a moderator's pending queue, and an account's own cases.
CREATE INDEX verification_case_status_created_idx
    ON verification.verification_case (status, created_at);
CREATE INDEX verification_case_account_idx
    ON verification.verification_case (account_id);

-- Append-only audit trail for verification decisions (SECURITY_PRIVACY.md §4; every decision has a
-- reason code, TRUST_VERIFICATION.md §7). The application only ever inserts here.
--
-- No FK on actor_account_id (identity's table — boundary rules) or on case_id: an action against a
-- case that does not exist must still be auditable (outcome NOT_FOUND), the same decision made for
-- reviews' V4.3 and properties' V3.3.
CREATE TABLE verification.verification_decision_audit_event (
    id                UUID        PRIMARY KEY,
    -- Nullable only for system-initiated expiry; a human decision always records its actor.
    actor_account_id  UUID,
    action            VARCHAR(20) NOT NULL
                      CHECK (action IN ('APPROVE', 'REJECT', 'EXPIRE', 'CANCEL', 'REVOKE')),
    case_id           UUID        NOT NULL,
    reason_code       VARCHAR(64) NOT NULL CHECK (length(btrim(reason_code)) > 0),
    outcome           VARCHAR(20) NOT NULL CHECK (outcome IN ('APPLIED', 'NOT_FOUND')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT verification_decision_human_action_has_actor
        CHECK (action = 'EXPIRE' OR actor_account_id IS NOT NULL)
);

CREATE INDEX verification_decision_audit_case_idx
    ON verification.verification_decision_audit_event (case_id, created_at);
CREATE INDEX verification_decision_audit_actor_idx
    ON verification.verification_decision_audit_event (actor_account_id, created_at);

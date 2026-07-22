-- Append-only audit trail for moderation actions on reviews (SECURITY_PRIVACY.md: admin mutations
-- are audited; MODERATION.md: every action requires a reason code). The application only ever
-- inserts here; there is no update or delete path.
--
-- Neither account/review column is a foreign key, for the same two reasons as properties' V3.3 and
-- identity's V2.5:
--   * moderator_account_id belongs to the identity module — modules never reference another
--     module's tables (ARCHITECTURE boundary rules), so it is stored as an opaque UUID.
--   * review_id must stay writable even when no such review exists, so that a moderation action
--     against a missing id is still auditable (outcome NOT_FOUND).
--
-- review_version_id records which immutable content version the decision applied to — a decision
-- about content is meaningless in the trail without the content it judged. It is null when the
-- review was not found, and for a REMOVE of an empty draft.
CREATE TABLE reviews.review_moderation_audit_event (
    id                   UUID        PRIMARY KEY,
    moderator_account_id UUID        NOT NULL,
    action               VARCHAR(32) NOT NULL
                         CHECK (action IN ('PUBLISH', 'REJECT', 'HIDE', 'RESTORE', 'REMOVE')),
    review_id            UUID        NOT NULL,
    review_version_id    UUID,
    -- The moderation module will own the reason-code taxonomy; until then the code is free-form
    -- but mandatory — an unexplained moderation action is not allowed to exist.
    reason_code          VARCHAR(64) NOT NULL CHECK (length(btrim(reason_code)) > 0),
    outcome              VARCHAR(20) NOT NULL CHECK (outcome IN ('APPLIED', 'NOT_FOUND')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX review_moderation_audit_moderator_idx
    ON reviews.review_moderation_audit_event (moderator_account_id, created_at);
-- The per-review trail: everything moderation ever did to one review, in order.
CREATE INDEX review_moderation_audit_review_idx
    ON reviews.review_moderation_audit_event (review_id, created_at);

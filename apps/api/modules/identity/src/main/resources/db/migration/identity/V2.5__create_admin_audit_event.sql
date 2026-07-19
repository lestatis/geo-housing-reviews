-- Append-only audit trail for admin access to accounts (SECURITY_PRIVACY.md: "append-only audit
-- log with restricted access" for admin/personal-data access). The application only ever inserts
-- rows here; there is no update or delete path. The CHECKs pin action/outcome to the values this
-- chunk emits — a new action is added by a later migration, keeping the log's vocabulary explicit.
CREATE TABLE identity.admin_audit_event (
    id                UUID        PRIMARY KEY,
    admin_account_id  UUID        NOT NULL REFERENCES identity.account(id),
    action            VARCHAR(64) NOT NULL CHECK (action IN ('VIEW_ACCOUNT')),
    -- Not a foreign key on purpose: the log must record the id an admin tried to access even when
    -- no such account exists (an outcome of NOT_FOUND is itself an auditable event). The acting
    -- admin, by contrast, always exists, so admin_account_id stays a foreign key.
    target_account_id UUID,
    outcome           VARCHAR(20) NOT NULL CHECK (outcome IN ('FOUND', 'NOT_FOUND')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX admin_audit_event_admin_idx
    ON identity.admin_audit_event (admin_account_id, created_at);

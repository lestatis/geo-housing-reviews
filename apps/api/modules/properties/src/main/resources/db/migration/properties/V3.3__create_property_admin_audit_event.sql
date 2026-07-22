-- Append-only audit trail for admin lifecycle actions on properties (SECURITY_PRIVACY.md: admin
-- mutations are audited). The application only ever inserts here; there is no update or delete path.
--
-- Neither id column is a foreign key, for two different reasons:
--   * admin_account_id belongs to the identity module — modules never reference another module's
--     tables (ARCHITECTURE boundary rules), so it is stored as an opaque UUID.
--   * property_id must stay writable even when no such property exists, so that an admin action
--     against a missing id is still auditable (outcome NOT_FOUND). This mirrors the same decision
--     already made for identity's V2.5 audit table.
CREATE TABLE properties.property_admin_audit_event (
    id                 UUID        PRIMARY KEY,
    admin_account_id   UUID        NOT NULL,
    action             VARCHAR(32) NOT NULL CHECK (action IN ('ACTIVATE', 'HIDE', 'MERGE')),
    property_id        UUID,
    target_property_id UUID,
    outcome            VARCHAR(20) NOT NULL CHECK (outcome IN ('APPLIED', 'NOT_FOUND')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX property_admin_audit_admin_idx
    ON properties.property_admin_audit_event (admin_account_id, created_at);

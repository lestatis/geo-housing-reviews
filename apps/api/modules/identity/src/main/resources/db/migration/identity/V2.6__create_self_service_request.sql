-- Records identity-local data-subject requests (export/deletion) for idempotency and DSR audit.
-- The unique (account_id, idempotency_key) lets a client safely retry a request and lets the server
-- reject the same key reused for a different operation (API_GUIDELINES idempotency). The row does
-- not store the export payload — that is regenerated on replay so no extra copy of personal data is
-- persisted. Deletion itself is idempotent (Account.close/PublicProfile.anonymize are no-ops when
-- already applied), so this table gates key reuse, not the mutation.
CREATE TABLE identity.self_service_request (
    id              UUID         PRIMARY KEY,
    account_id      UUID         NOT NULL REFERENCES identity.account(id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_type    VARCHAR(20)  NOT NULL CHECK (request_type IN ('EXPORT', 'DELETE')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (account_id, idempotency_key)
);

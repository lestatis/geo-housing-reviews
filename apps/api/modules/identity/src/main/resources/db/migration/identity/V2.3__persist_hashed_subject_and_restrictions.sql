ALTER TABLE identity.account RENAME COLUMN auth_subject TO auth_subject_hash;

ALTER TABLE identity.account
    ALTER COLUMN auth_subject_hash TYPE CHAR(64);

CREATE TABLE identity.user_restriction (
    id                   UUID        PRIMARY KEY,
    account_id           UUID        NOT NULL REFERENCES identity.account(id),
    scope                VARCHAR(32) NOT NULL CHECK (scope IN ('ACCOUNT_WIDE')),
    reason               TEXT        NOT NULL CHECK (length(trim(reason)) > 0),
    start_at             TIMESTAMPTZ NOT NULL,
    end_at               TIMESTAMPTZ,
    moderator_account_id UUID        REFERENCES identity.account(id),
    appeal_status        VARCHAR(20) NOT NULL CHECK (appeal_status IN ('NONE', 'REQUESTED', 'GRANTED', 'DENIED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_at IS NULL OR end_at >= start_at)
);

CREATE INDEX user_restriction_account_active_idx
    ON identity.user_restriction (account_id, start_at, end_at);

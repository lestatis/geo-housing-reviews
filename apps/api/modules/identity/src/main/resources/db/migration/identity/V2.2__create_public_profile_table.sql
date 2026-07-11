CREATE TABLE identity.public_profile (
    account_id  UUID PRIMARY KEY REFERENCES identity.account(id),
    pseudonym   VARCHAR(32)  NOT NULL UNIQUE,
    avatar_url  VARCHAR(2048),
    locale      VARCHAR(10)  NOT NULL DEFAULT 'en',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

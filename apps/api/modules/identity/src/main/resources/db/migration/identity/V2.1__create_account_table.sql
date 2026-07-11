CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.account (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_subject  VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(320),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0
);

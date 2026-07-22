-- Reviews module core tables (docs/DOMAIN_MODEL.md Review context).
--
-- property_id and author_account_id are opaque UUIDs, NOT foreign keys: properties and accounts
-- belong to other modules and no module references another module's tables (ARCHITECTURE boundary
-- rules). Existence/reviewability of a property is checked through the properties module's public
-- contract at the application layer.
--
-- A review's content is versioned and immutable: an edit appends a new review_version row and moves
-- review.current_version_id, so the edit trail survives for moderation.

CREATE SCHEMA IF NOT EXISTS reviews;

CREATE TABLE reviews.review (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id        UUID        NOT NULL,
    author_account_id  UUID        NOT NULL,
    relationship_type  VARCHAR(30) NOT NULL
                       CHECK (relationship_type IN
                              ('CURRENT_RESIDENT', 'FORMER_RESIDENT', 'OWNER', 'OTHER')),
    residence_from     DATE,
    residence_to       DATE,
    status             VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'PENDING_MODERATION', 'PUBLISHED',
                                         'REJECTED', 'HIDDEN', 'REMOVED')),
    current_version_id UUID,
    -- Verification is a projection owned by the verification module. Never a bare verified=true:
    -- the tier records how the relationship was evidenced (TRUST_VERIFICATION.md §2/§3), and Tier 0
    -- content still publishes.
    verification_tier  VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED'
                       CHECK (verification_tier IN
                              ('UNVERIFIED', 'RELATIONSHIP_SIGNAL', 'DOCUMENT_VERIFIED')),
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CHECK (residence_from IS NULL OR residence_to IS NULL OR residence_to >= residence_from),
    -- A published review must record when it was published.
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE TABLE reviews.review_version (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id      UUID        NOT NULL REFERENCES reviews.review(id),
    version_number INT         NOT NULL CHECK (version_number > 0),
    locale         VARCHAR(10) NOT NULL,
    body           TEXT        NOT NULL CHECK (length(btrim(body)) > 0),
    pros           TEXT,
    cons           TEXT,
    recommendation VARCHAR(20) NOT NULL
                   CHECK (recommendation IN ('RECOMMEND', 'NEUTRAL', 'NOT_RECOMMEND')),
    -- Null for the first version; an edit must say why.
    edit_reason    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_id, version_number)
);

CREATE TABLE reviews.category_rating (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_version_id    UUID        NOT NULL REFERENCES reviews.review_version(id),
    category             VARCHAR(50) NOT NULL,
    value                SMALLINT CHECK (value BETWEEN 1 AND 5),
    not_applicable       BOOLEAN     NOT NULL DEFAULT false,
    note                 TEXT,
    -- Category definitions are versioned so historical ratings stay interpretable
    -- (docs/DOMAIN_MODEL.md CategoryRating).
    category_set_version INT         NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Exactly one of "not applicable" or a value.
    CHECK ((not_applicable AND value IS NULL) OR (NOT not_applicable AND value IS NOT NULL)),
    UNIQUE (review_version_id, category)
);

-- The reference is circular (review -> current version -> review), so it is added once both tables
-- exist.
ALTER TABLE reviews.review
    ADD CONSTRAINT review_current_version_fkey
    FOREIGN KEY (current_version_id) REFERENCES reviews.review_version(id);

-- One live review per author per property. Rejected/removed reviews are terminal and do not block
-- the author from starting again; an update to a live review appends a version instead.
CREATE UNIQUE INDEX review_one_live_per_author_property_idx
    ON reviews.review (author_account_id, property_id)
    WHERE status NOT IN ('REJECTED', 'REMOVED');

-- Read paths: a property's published reviews, and an author's own reviews.
CREATE INDEX review_property_status_published_idx
    ON reviews.review (property_id, status, published_at DESC);
CREATE INDEX review_author_idx ON reviews.review (author_account_id);
CREATE INDEX review_version_review_idx ON reviews.review_version (review_id);
CREATE INDEX category_rating_version_idx ON reviews.category_rating (review_version_id);

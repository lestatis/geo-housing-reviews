-- Properties module catalogue: canonical properties with structured addresses, multilingual
-- aliases, and source provenance (docs/ARCHITECTURE.md §5, docs/DOMAIN_MODEL.md Property context).
-- Coordinates are plain latitude/longitude for now; a PostGIS geography column and spatial mapping
-- are added in the duplicate/geo chunk behind an ADR, so no geo dependency is pulled in yet.
--
-- created_by holds the identity account id as an opaque UUID, NOT a foreign key: modules own their
-- tables and never reference another module's tables directly (docs/ARCHITECTURE.md boundary rules).

CREATE SCHEMA IF NOT EXISTS properties;

CREATE TABLE properties.address (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country       VARCHAR(2)   NOT NULL DEFAULT 'GE',
    city          VARCHAR(200),
    district      VARCHAR(200),
    street        VARCHAR(300),
    building      VARCHAR(100),
    original_text TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE properties.property (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                    VARCHAR(30) NOT NULL
                            CHECK (type IN ('BUILDING', 'RESIDENTIAL_COMPLEX', 'BLOCK', 'PHASE')),
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT', 'ACTIVE', 'MERGED', 'HIDDEN')),
    canonical_name          VARCHAR(300) NOT NULL CHECK (length(trim(canonical_name)) > 0),
    address_id              UUID REFERENCES properties.address(id),
    parent_property_id      UUID REFERENCES properties.property(id),
    merged_into_property_id UUID REFERENCES properties.property(id),
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    created_by              UUID        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 0,
    -- A merge target may only be set once the property is actually merged away.
    CHECK (merged_into_property_id IS NULL OR status = 'MERGED'),
    -- A property cannot be its own parent or its own merge target.
    CHECK (parent_property_id IS NULL OR parent_property_id <> id),
    CHECK (merged_into_property_id IS NULL OR merged_into_property_id <> id)
);

CREATE TABLE properties.property_alias (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID         NOT NULL REFERENCES properties.property(id),
    locale      VARCHAR(10)  NOT NULL,
    name        VARCHAR(300) NOT NULL CHECK (length(trim(name)) > 0),
    source      VARCHAR(30)  NOT NULL,
    confidence  NUMERIC(3, 2) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE properties.property_source (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID        NOT NULL REFERENCES properties.property(id),
    source_type VARCHAR(50) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX property_status_idx ON properties.property (status);
CREATE INDEX property_alias_property_idx ON properties.property_alias (property_id);
CREATE INDEX property_source_property_idx ON properties.property_source (property_id);

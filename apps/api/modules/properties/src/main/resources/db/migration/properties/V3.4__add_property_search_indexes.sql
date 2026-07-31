-- Search support for the catalogue (ARCHITECTURE.md §8: start with PostgreSQL — trigram search for
-- aliases and address fragments, PostGIS for distance).
--
-- Trigram rather than tsvector, deliberately. The catalogue is multilingual (P-002: Russian and
-- English at launch, Georgian data model ready), and full-text search needs a per-language
-- configuration; PostgreSQL ships one for russian and english but none for Georgian, so a tsvector
-- would silently degrade to 'simple' for exactly the market this launches in. Trigram is
-- language-agnostic and matches the partial, misspelt input people actually type.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- The three things someone types when looking for their building: its name, another name for it,
-- or where it is.
CREATE INDEX property_canonical_name_trgm_idx
    ON properties.property USING GIN (lower(canonical_name) gin_trgm_ops);

CREATE INDEX property_alias_name_trgm_idx
    ON properties.property_alias USING GIN (lower(name) gin_trgm_ops);

CREATE INDEX address_street_trgm_idx
    ON properties.address USING GIN (lower(street) gin_trgm_ops);

CREATE INDEX address_city_trgm_idx
    ON properties.address USING GIN (lower(city) gin_trgm_ops);

-- Search only ever returns ACTIVE rows, so the status filter is worth having in an index rather
-- than scanning the catalogue and discarding drafts and merged records afterwards.
CREATE INDEX property_active_idx
    ON properties.property (status)
    WHERE status = 'ACTIVE';

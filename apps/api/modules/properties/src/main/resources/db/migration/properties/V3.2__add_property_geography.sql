-- Duplicate-candidate support (ADR-0007): a PostGIS geography point derived from the existing
-- latitude/longitude, plus the indexes the deterministic finder needs. The application never writes
-- `geo` (it is GENERATED from lat/lng, the single source of truth) and no JPA entity maps it — it is
-- read only by the native duplicate query, so no hibernate-spatial dependency is required.
ALTER TABLE properties.property
    ADD COLUMN geo geography(Point, 4326)
    GENERATED ALWAYS AS (
        CASE
            WHEN latitude IS NOT NULL AND longitude IS NOT NULL
            THEN ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
        END
    ) STORED;

-- Index-assist ST_DWithin proximity search.
CREATE INDEX property_geo_idx ON properties.property USING GIST (geo);

-- Back the normalized canonical-name equality match.
CREATE INDEX property_canonical_name_norm_idx
    ON properties.property (lower(btrim(canonical_name)));

-- Fix-forward on V3.4's partial index.
--
-- V3.4 indexed `status = 'ACTIVE'` because the search query filtered to ACTIVE. That was wrong: this
-- module's own rule (PropertyCatalogService.visibilityOf) is that DRAFT properties are
-- user-contributed and already publicly readable, and only an administrator hiding one withholds it.
-- Filtering search to ACTIVE meant a resident could create a property, review it, and then never
-- find it again — including their own.
--
-- Searchable is therefore "not withheld and not superseded": everything except HIDDEN and MERGED.
DROP INDEX IF EXISTS properties.property_active_idx;

CREATE INDEX property_searchable_idx
    ON properties.property (status)
    WHERE status NOT IN ('HIDDEN', 'MERGED');

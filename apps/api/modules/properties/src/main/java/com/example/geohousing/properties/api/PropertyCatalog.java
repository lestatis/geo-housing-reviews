package com.example.geohousing.properties.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The properties module's published contract: what other modules may ask about a property.
 *
 * <p>Everything here is deliberately narrow. Other modules see identifiers and whether a property
 * is publicly available — not the internal lifecycle, the address model, or the aggregate — so the
 * catalogue can evolve without breaking them (ARCHITECTURE: modules own their data and expose
 * explicit application interfaces).
 */
public interface PropertyCatalog {

  /**
   * Looks up a property, following merges to the record that survived them, or empty if no such
   * property exists.
   *
   * <p>Callers get the surviving property because a merged one is an alias for it: a review, a
   * photo or a search hit should attach to the building that is still real. The returned summary
   * therefore never describes a merged property, and its {@link PropertySummary#propertyId()} may
   * differ from the requested id.
   *
   * @throws UnresolvableMergeChainException if the merge chain does not end — corrupt catalogue
   *     data rather than a caller error
   */
  Optional<PropertySummary> findSurviving(UUID propertyId);
}

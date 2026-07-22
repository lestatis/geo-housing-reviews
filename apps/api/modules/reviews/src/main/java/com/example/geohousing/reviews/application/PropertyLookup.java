package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.PropertyRef;
import java.util.Optional;

/**
 * Outbound port asking the properties module about a property a review points at. The reviews
 * module never reads the properties tables (ARCHITECTURE: modules own their data); the adapter that
 * implements this calls the properties module's published api.
 *
 * <p>This is intentionally the smallest question reviews needs answered: does the property exist,
 * may it be reviewed, and — since properties can be merged — which property should the review
 * actually attach to.
 */
public interface PropertyLookup {

  /**
   * Looks up how a property may be reviewed, or empty if no such property exists.
   *
   * <p>Merge chains are resolved by the properties module, so the returned {@link
   * PropertyReviewability#reviewTarget()} is the surviving property.
   */
  Optional<PropertyReviewability> findReviewability(PropertyRef propertyRef);
}

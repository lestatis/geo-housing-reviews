package com.example.geohousing.properties.application;

import java.util.List;
import java.util.Objects;

/** Finds properties in the catalogue by what someone typed, where they are, or both. */
public final class PropertySearchService {

  private final PropertyRepository propertyRepository;

  public PropertySearchService(PropertyRepository propertyRepository) {
    this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
  }

  /**
   * Ranked matches, best first.
   *
   * <p>The query object has already validated and clamped itself, so this is deliberately thin —
   * the ranking that matters is the database's, and duplicating any of it here would create a
   * second place for relevance to disagree with itself.
   */
  public List<PropertyMatch> search(PropertySearchQuery query) {
    Objects.requireNonNull(query, "query");
    return propertyRepository.search(
        query.text(), query.point(), query.radiusMeters(), query.limit());
  }
}

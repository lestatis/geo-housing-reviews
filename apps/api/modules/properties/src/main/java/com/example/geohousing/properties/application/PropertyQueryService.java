package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import java.util.List;
import java.util.Objects;

/** Reads properties from the catalogue. */
public final class PropertyQueryService {

  /** Applied when the caller asks for nothing specific. */
  public static final int DEFAULT_LIMIT = 20;

  /** Hard cap, so a client cannot ask for the whole catalogue in one call. */
  public static final int MAX_LIMIT = 50;

  private final PropertyRepository propertyRepository;

  public PropertyQueryService(PropertyRepository propertyRepository) {
    this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
  }

  public Property getById(PropertyId propertyId) {
    return propertyRepository
        .findById(Objects.requireNonNull(propertyId, "propertyId"))
        .orElseThrow(() -> new PropertyNotFoundException(propertyId));
  }

  /**
   * The most recently created properties, newest first. The requested limit is clamped to {@code
   * [1, MAX_LIMIT]}; richer matching is PropertySearchService's job.
   */
  public List<Property> listRecent(Integer requestedLimit) {
    int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
    return propertyRepository.findRecent(Math.clamp(limit, 1, MAX_LIMIT));
  }
}

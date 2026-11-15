package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import com.example.geohousing.properties.domain.PropertyStatus;
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
   * A property as the public catalogue may show it. A hidden property is reported exactly like an
   * unknown one, so the response does not confirm that an administrator withdrew it. A merged
   * property stays readable as a tombstone pointing at the record that survived.
   */
  public Property getPublicById(PropertyId propertyId) {
    Property property = getById(propertyId);
    if (property.status() == PropertyStatus.HIDDEN) {
      throw new PropertyNotFoundException(propertyId);
    }
    return property;
  }

  /**
   * The most recently created publicly visible properties, newest first. Hidden and merged
   * properties are withheld, matching search. The requested limit is clamped to {@code [1,
   * MAX_LIMIT]}; richer matching is PropertySearchService's job.
   */
  public List<Property> listRecentPublic(Integer requestedLimit) {
    int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
    return propertyRepository.findRecent(Math.clamp(limit, 1, MAX_LIMIT)).stream()
        .filter(PropertyQueryService::isVisibleInPublicList)
        .toList();
  }

  /**
   * The persistence query already excludes these statuses. Filtering here as well keeps the
   * public-read rule true at the application boundary even if an adapter forgets it.
   */
  private static boolean isVisibleInPublicList(Property property) {
    return property.status() != PropertyStatus.HIDDEN && property.status() != PropertyStatus.MERGED;
  }
}

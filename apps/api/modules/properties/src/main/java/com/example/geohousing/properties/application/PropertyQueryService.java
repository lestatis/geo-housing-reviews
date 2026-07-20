package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import java.util.Objects;

/** Reads properties from the catalogue. */
public final class PropertyQueryService {

  private final PropertyRepository propertyRepository;

  public PropertyQueryService(PropertyRepository propertyRepository) {
    this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
  }

  public Property getById(PropertyId propertyId) {
    return propertyRepository
        .findById(Objects.requireNonNull(propertyId, "propertyId"))
        .orElseThrow(() -> new PropertyNotFoundException(propertyId));
  }
}

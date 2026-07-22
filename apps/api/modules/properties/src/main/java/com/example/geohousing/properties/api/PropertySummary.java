package com.example.geohousing.properties.api;

import java.util.Objects;
import java.util.UUID;

/**
 * What another module is told about a property.
 *
 * @param propertyId the surviving property's id, which differs from the requested one when the
 *     property had been merged
 * @param canonicalName the catalogue's name for it, so a consumer can name the property it acted on
 *     without reading the properties tables
 * @param visibility whether the property is publicly available
 */
public record PropertySummary(
    UUID propertyId, String canonicalName, PropertyVisibility visibility) {

  public PropertySummary {
    Objects.requireNonNull(propertyId, "propertyId");
    Objects.requireNonNull(canonicalName, "canonicalName");
    Objects.requireNonNull(visibility, "visibility");
  }

  public boolean isPublic() {
    return visibility == PropertyVisibility.PUBLIC;
  }
}

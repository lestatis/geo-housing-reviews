package com.example.geohousing.properties.domain;

/** Raised when a property cannot be found for a given identifier. */
public class PropertyNotFoundException extends RuntimeException {

  public PropertyNotFoundException(PropertyId propertyId) {
    super("property not found: " + propertyId.value());
  }
}

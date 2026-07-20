package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.PropertyType;
import java.util.Objects;

/**
 * Request to create a property. {@code address} and {@code coordinates} are optional. {@code
 * allowDuplicate} lets the caller proceed after being shown duplicate candidates — when false (the
 * default first attempt), creation stops and returns the candidates instead.
 */
public record CreatePropertyCommand(
    PropertyType type,
    String canonicalName,
    CreatorId createdBy,
    Address address,
    Coordinates coordinates,
    boolean allowDuplicate) {

  public CreatePropertyCommand {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(createdBy, "createdBy");
    if (canonicalName == null || canonicalName.isBlank()) {
      throw new IllegalArgumentException("canonicalName must not be blank");
    }
  }
}

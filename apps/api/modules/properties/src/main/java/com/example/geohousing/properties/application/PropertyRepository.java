package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import java.util.Optional;

/** Application port for persisting and reading properties without exposing persistence details. */
public interface PropertyRepository {

  Optional<Property> findById(PropertyId propertyId);

  /** Persists a newly created property (with its address, aliases and sources) atomically. */
  void create(Property property);
}

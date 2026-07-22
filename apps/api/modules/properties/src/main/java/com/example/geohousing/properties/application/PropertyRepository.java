package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import java.util.List;
import java.util.Optional;

/** Application port for persisting and reading properties without exposing persistence details. */
public interface PropertyRepository {

  Optional<Property> findById(PropertyId propertyId);

  /** The most recently created properties, newest first, bounded by {@code limit}. */
  List<Property> findRecent(int limit);

  /** Persists a newly created property (with its address, aliases and sources) atomically. */
  void create(Property property);
}

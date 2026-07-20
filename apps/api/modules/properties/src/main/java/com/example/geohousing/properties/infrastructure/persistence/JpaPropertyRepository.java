package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the {@link PropertyRepository} port. */
@Repository
public class JpaPropertyRepository implements PropertyRepository {

  private final SpringDataPropertyRepository properties;

  public JpaPropertyRepository(SpringDataPropertyRepository properties) {
    this.properties = properties;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Property> findById(PropertyId propertyId) {
    return properties.findById(propertyId.value()).map(PropertyJpaMapper::toDomain);
  }

  @Override
  @Transactional
  public void create(Property property) {
    properties.save(PropertyJpaMapper.toEntity(property));
  }
}

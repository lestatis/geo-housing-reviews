package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.application.PropertyMatch;
import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
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
  @Transactional(readOnly = true)
  public List<Property> findRecent(int limit) {
    return properties.findRecent(Limit.of(limit)).stream()
        .map(PropertyJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void create(Property property) {
    properties.save(PropertyJpaMapper.toEntity(property));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertyMatch> search(
      String text, Coordinates point, double radiusMeters, int limit) {
    boolean hasText = text != null && !text.isBlank();
    boolean hasPoint = point != null;
    return properties
        .search(
            hasText,
            hasText ? text.trim() : "",
            hasPoint,
            hasPoint ? point.latitude() : 0d,
            hasPoint ? point.longitude() : 0d,
            radiusMeters,
            limit)
        .stream()
        .map(
            row ->
                new PropertyMatch(
                    PropertyId.of(row.getId()),
                    row.getCanonicalName(),
                    row.getScore(),
                    row.getDistanceMeters()))
        .toList();
  }
}

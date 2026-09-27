package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.application.PropertyAddressSummary;
import com.example.geohousing.properties.application.PropertyMatch;
import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
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
                    PropertyType.valueOf(row.getType()),
                    addressSummary(row),
                    row.getScore(),
                    row.getDistanceMeters()))
        .toList();
  }

  /**
   * The address summary, or {@code null} when the row carries no address at all.
   *
   * <p>The search query LEFT JOINs the address table because it matches against {@code street} and
   * {@code city}, so a property with no address arrives as four null columns rather than as a row
   * that is missing. Treating that as "no address" is what lets a result row stay honest instead of
   * printing an empty address line.
   */
  private static PropertyAddressSummary addressSummary(PropertySearchProjection row) {
    if (row.getCity() == null
        && row.getDistrict() == null
        && row.getStreet() == null
        && row.getBuilding() == null) {
      return null;
    }
    return new PropertyAddressSummary(
        row.getCity(), row.getDistrict(), row.getStreet(), row.getBuilding());
  }
}

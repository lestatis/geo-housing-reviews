package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.application.DuplicateCandidate;
import com.example.geohousing.properties.application.DuplicateCandidateFinder;
import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class PropertyDuplicateDetectionIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private PropertyRepository properties;
  @Autowired private DuplicateCandidateFinder finder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void matchesOnNormalizedCanonicalName() {
    PropertyId id = create("Vake Tower", null);

    assertThat(finder.findCandidates("  vake tower  ", null, null))
        .extracting(DuplicateCandidate::propertyId)
        .contains(id);
  }

  @Test
  void matchesByGeographicProximityWithinTheRadius() {
    PropertyId id = create("Unique Name A1B2C3", Coordinates.of(41.709, 44.786));

    // A different name but a point ~30 m away is still a candidate.
    assertThat(
            finder.findCandidates("Totally Different Name", null, Coordinates.of(41.7092, 44.7862)))
        .extracting(DuplicateCandidate::propertyId)
        .contains(id);

    // A point kilometres away is not.
    assertThat(finder.findCandidates("Another Name", null, Coordinates.of(42.0, 45.0)))
        .extracting(DuplicateCandidate::propertyId)
        .doesNotContain(id);
  }

  @Test
  void excludesMergedProperties() {
    PropertyId target = create("Merge Target Building", null);
    PropertyId id = create("Merged Away Building", null);
    // The merge target must reference a real property (foreign key), so point it at `target`.
    jdbcTemplate.update(
        "update properties.property set status = 'MERGED', merged_into_property_id = ? where id = ?",
        target.value(),
        id.value());

    assertThat(finder.findCandidates("Merged Away Building", null, null))
        .extracting(DuplicateCandidate::propertyId)
        .doesNotContain(id);
  }

  @Test
  void returnsEmptyWhenNothingMatches() {
    assertThat(finder.findCandidates("No Such Property " + UUID.randomUUID(), null, null))
        .isEmpty();
  }

  @Test
  void theGeneratedGeoColumnIsPopulatedFromCoordinates() {
    PropertyId withCoords = create("Geo Row", Coordinates.of(41.70, 44.80));
    PropertyId withoutCoords = create("No Geo Row", null);

    assertThat(geoIsNull(withCoords)).isFalse();
    assertThat(geoIsNull(withoutCoords)).isTrue();
  }

  private PropertyId create(String name, Coordinates coordinates) {
    PropertyId id = PropertyId.of(UUID.randomUUID());
    Property property =
        Property.create(id, PropertyType.BUILDING, name, CreatorId.of(UUID.randomUUID()), CLOCK);
    if (coordinates != null) {
      property.setCoordinates(coordinates, CLOCK);
    }
    properties.create(property);
    return id;
  }

  private Boolean geoIsNull(PropertyId id) {
    return jdbcTemplate.queryForObject(
        "select geo is null from properties.property where id = ?", Boolean.class, id.value());
  }
}

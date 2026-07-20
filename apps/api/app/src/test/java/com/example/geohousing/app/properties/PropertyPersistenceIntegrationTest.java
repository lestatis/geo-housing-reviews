package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.AliasSource;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAlias;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertySource;
import com.example.geohousing.properties.domain.PropertyStatus;
import com.example.geohousing.properties.domain.PropertyType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class PropertyPersistenceIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private PropertyRepository properties;

  @Test
  void persistsAndReconstitutesTheWholeAggregate() {
    PropertyId id = PropertyId.of(UUID.randomUUID());
    CreatorId creator = CreatorId.of(UUID.randomUUID());
    Address address =
        new Address("GE", "Tbilisi", "Vake", "Chavchavadze Ave", "37", "37 Chavchavadze Ave");
    Property property =
        Property.create(id, PropertyType.RESIDENTIAL_COMPLEX, "Vake Tower", creator, CLOCK);
    property.setAddress(address, CLOCK);
    property.setCoordinates(Coordinates.of(41.709, 44.786), CLOCK);
    property.addAlias(
        new PropertyAlias("ka", "ვაკე თაუერი", AliasSource.USER_SUBMITTED, 0.9), CLOCK);
    property.addAlias(new PropertyAlias("en", "Vake Tower", AliasSource.OFFICIAL, null), CLOCK);
    property.addSource(
        new PropertySource("city-registry", Instant.parse("2026-01-01T00:00:00Z"), "imported"),
        CLOCK);

    properties.create(property);

    Property stored = properties.findById(id).orElseThrow();
    assertThat(stored.type()).isEqualTo(PropertyType.RESIDENTIAL_COMPLEX);
    assertThat(stored.status()).isEqualTo(PropertyStatus.DRAFT);
    assertThat(stored.canonicalName()).isEqualTo("Vake Tower");
    assertThat(stored.createdBy()).isEqualTo(creator);
    assertThat(stored.address()).contains(address);
    assertThat(stored.coordinates()).contains(Coordinates.of(41.709, 44.786));
    assertThat(stored.aliases())
        .containsExactlyInAnyOrder(
            new PropertyAlias("ka", "ვაკე თაუერი", AliasSource.USER_SUBMITTED, 0.9),
            new PropertyAlias("en", "Vake Tower", AliasSource.OFFICIAL, null));
    assertThat(stored.sources()).hasSize(1);
    assertThat(stored.sources().get(0).sourceType()).isEqualTo("city-registry");
    assertThat(stored.sources().get(0).note()).isEqualTo("imported");
  }

  @Test
  void persistsAMinimalPropertyWithoutAddressCoordinatesOrChildren() {
    PropertyId id = PropertyId.of(UUID.randomUUID());
    Property property =
        Property.create(
            id, PropertyType.BUILDING, "Bare Building", CreatorId.of(UUID.randomUUID()), CLOCK);

    properties.create(property);

    Property stored = properties.findById(id).orElseThrow();
    assertThat(stored.address()).isEmpty();
    assertThat(stored.coordinates()).isEmpty();
    assertThat(stored.aliases()).isEmpty();
    assertThat(stored.sources()).isEmpty();
    assertThat(stored.version()).isZero();
  }

  @Test
  void returnsEmptyForAnUnknownProperty() {
    assertThat(properties.findById(PropertyId.of(UUID.randomUUID()))).isEmpty();
  }
}

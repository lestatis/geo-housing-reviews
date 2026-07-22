package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import com.example.geohousing.reviews.application.PropertyLookup;
import com.example.geohousing.reviews.application.PropertyReviewability;
import com.example.geohousing.reviews.domain.PropertyRef;
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

/**
 * The first cross-module call in the application: reviews asking the properties module about a
 * property, through the real Spring wiring and real data rather than a fake catalogue.
 */
@Testcontainers
@SpringBootTest
class PropertyLookupIntegrationTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private PropertyLookup propertyLookup;
  @Autowired private PropertyRepository properties;

  private Property storeProperty(String canonicalName) {
    Property property =
        Property.create(
            PropertyId.of(UUID.randomUUID()),
            PropertyType.BUILDING,
            canonicalName,
            CreatorId.of(UUID.randomUUID()),
            CLOCK);
    properties.create(property);
    return property;
  }

  @Test
  void reviewsCanAskWhetherARealPropertyAcceptsReviews() {
    Property property = storeProperty("Vake Tower");

    PropertyReviewability reviewability =
        propertyLookup.findReviewability(PropertyRef.of(property.id().value())).orElseThrow();

    assertThat(reviewability.reviewTarget()).isEqualTo(PropertyRef.of(property.id().value()));
    assertThat(reviewability.acceptsNewReviews()).isTrue();
  }

  @Test
  void aReviewOfAMergedPropertyIsDirectedToTheSurvivingOne() {
    Property survivor = storeProperty("Vake Tower");
    Property duplicate =
        Property.create(
            PropertyId.of(UUID.randomUUID()),
            PropertyType.BUILDING,
            "vake tower",
            CreatorId.of(UUID.randomUUID()),
            CLOCK);
    duplicate.mergeInto(survivor.id(), CLOCK);
    properties.create(duplicate);

    PropertyReviewability reviewability =
        propertyLookup.findReviewability(PropertyRef.of(duplicate.id().value())).orElseThrow();

    assertThat(reviewability.reviewTarget()).isEqualTo(PropertyRef.of(survivor.id().value()));
    assertThat(reviewability.acceptsNewReviews()).isTrue();
  }

  @Test
  void aPropertyThatDoesNotExistIsReportedAsAbsent() {
    assertThat(propertyLookup.findReviewability(PropertyRef.of(UUID.randomUUID()))).isEmpty();
  }
}

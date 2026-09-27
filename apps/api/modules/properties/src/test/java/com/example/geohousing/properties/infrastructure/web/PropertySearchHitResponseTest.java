package com.example.geohousing.properties.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.application.PropertyAddressSummary;
import com.example.geohousing.properties.application.PropertyMatch;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import java.lang.reflect.RecordComponent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The public search-hit shape, checked at the seam that decides what a client sees.
 *
 * <p>The address cases live here as well as in the Postgres integration test on purpose: the SQL
 * test proves the columns come back, and this proves what leaves the module — including that the
 * summary has no field a result row must not publish.
 */
class PropertySearchHitResponseTest {

  @Test
  void aFullAddressBecomesAnAddressSummary() {
    PropertySearchHitResponse hit =
        PropertySearchHitResponse.from(
            match(new PropertyAddressSummary("Batumi", "Old Batumi", "Chavchavadze Avenue", "12")));

    assertThat(hit.propertyId()).isNotBlank();
    assertThat(hit.canonicalName()).isEqualTo("Orbi Sea Towers");
    assertThat(hit.type()).isEqualTo("RESIDENTIAL_COMPLEX");
    assertThat(hit.address())
        .isEqualTo(
            new PropertySearchHitResponse.AddressSummaryView(
                "Batumi", "Old Batumi", "Chavchavadze Avenue", "12"));
  }

  @Test
  void aPartialAddressKeepsThePartsItHasAndLeavesTheRestNull() {
    // "Orbi" plus a district is enough to pick between two similarly-named buildings.
    PropertySearchHitResponse hit =
        PropertySearchHitResponse.from(
            match(new PropertyAddressSummary(null, "Old Batumi", null, null)));

    assertThat(hit.address())
        .isEqualTo(
            new PropertySearchHitResponse.AddressSummaryView(null, "Old Batumi", null, null));
  }

  @Test
  void aPropertyWithNoAddressHasNoAddressSummary() {
    assertThat(PropertySearchHitResponse.from(match(null)).address()).isNull();
  }

  @Test
  void theSummaryCarriesOnlyTheSafeAddressParts() {
    // Pins the shape: adding originalText (free text that can hide an apartment number) or the
    // constant country code to the public hit fails here rather than in review.
    assertThat(PropertySearchHitResponse.AddressSummaryView.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("city", "district", "street", "building");
  }

  @Test
  void theHitStillCarriesTheScoreAndDistanceTheContractAlreadyExposed() {
    PropertySearchHitResponse hit =
        PropertySearchHitResponse.from(
            new PropertyMatch(
                PropertyId.of(UUID.randomUUID()),
                "Orbi Sea Towers",
                PropertyType.BUILDING,
                null,
                0.87,
                1250.0));

    assertThat(hit.score()).isEqualTo(0.87);
    assertThat(hit.distanceMeters()).isEqualTo(1250.0);
  }

  private static PropertyMatch match(PropertyAddressSummary address) {
    return new PropertyMatch(
        PropertyId.of(UUID.randomUUID()),
        "Orbi Sea Towers",
        PropertyType.RESIDENTIAL_COMPLEX,
        address,
        1.0,
        null);
  }
}

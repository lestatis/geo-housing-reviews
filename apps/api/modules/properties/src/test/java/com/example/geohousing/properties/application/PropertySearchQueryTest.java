package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.domain.Coordinates;
import org.junit.jupiter.api.Test;

class PropertySearchQueryTest {

  private static final Coordinates BATUMI = Coordinates.of(41.6412, 41.6300);

  @Test
  void aSearchNeedsSomethingToSearchFor() {
    // An empty search would return the catalogue ordered by nothing in particular — that is the
    // listing endpoint's job, and at scale it is a table scan any caller could trigger at will.
    assertThatThrownBy(() -> PropertySearchQuery.of(null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PropertySearchQuery.of("   ", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void textAloneIsEnough() {
    PropertySearchQuery query = PropertySearchQuery.of("orbi", null, null, null, null);

    assertThat(query.textFragment()).contains("orbi");
    assertThat(query.centre()).isEmpty();
  }

  @Test
  void aPointAloneIsEnough() {
    PropertySearchQuery query =
        PropertySearchQuery.of(null, BATUMI.latitude(), BATUMI.longitude(), null, null);

    assertThat(query.textFragment()).isEmpty();
    assertThat(query.centre()).contains(BATUMI);
  }

  @Test
  void surroundingSpaceIsNotPartOfWhatSomeoneTyped() {
    assertThat(PropertySearchQuery.of("  orbi  ", null, null, null, null).textFragment())
        .contains("orbi");
  }

  @Test
  void halfAPointIsNoPoint() {
    // A latitude without a longitude is not somewhere; treating it as one would search from the
    // equator and quietly return nothing useful.
    assertThat(PropertySearchQuery.of("orbi", 41.64, null, null, null).centre()).isEmpty();
    assertThat(PropertySearchQuery.of("orbi", null, 41.63, null, null).centre()).isEmpty();
  }

  @Test
  void theLimitIsClampedSoNobodyPullsTheWholeCatalogue() {
    assertThat(PropertySearchQuery.of("orbi", null, null, null, null).limit())
        .isEqualTo(PropertySearchQuery.DEFAULT_LIMIT);
    assertThat(PropertySearchQuery.of("orbi", null, null, null, 0).limit())
        .isEqualTo(PropertySearchQuery.DEFAULT_LIMIT);
    assertThat(PropertySearchQuery.of("orbi", null, null, null, -5).limit())
        .isEqualTo(PropertySearchQuery.DEFAULT_LIMIT);
    assertThat(PropertySearchQuery.of("orbi", null, null, null, 5).limit()).isEqualTo(5);
    assertThat(PropertySearchQuery.of("orbi", null, null, null, 5000).limit())
        .isEqualTo(PropertySearchQuery.MAX_LIMIT);
  }

  @Test
  void theRadiusIsClampedSoNearbyKeepsMeaningSomething() {
    assertThat(PropertySearchQuery.of("orbi", null, null, null, null).radiusMeters())
        .isEqualTo(PropertySearchQuery.DEFAULT_RADIUS_METERS);
    assertThat(PropertySearchQuery.of("orbi", null, null, 500d, null).radiusMeters())
        .isEqualTo(500d);
    assertThat(PropertySearchQuery.of("orbi", null, null, 10_000_000d, null).radiusMeters())
        .isEqualTo(PropertySearchQuery.MAX_RADIUS_METERS);
  }

  @Test
  void anImpossibleCoordinateIsRejectedRatherThanSearchedFor() {
    assertThatThrownBy(() -> PropertySearchQuery.of(null, 91d, 41.63, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

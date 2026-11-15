package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyMatchTest {

  @Test
  void aHitCarriesWhatItMatchedAndHowWell() {
    PropertyId id = PropertyId.of(UUID.randomUUID());

    PropertyMatch match =
        new PropertyMatch(id, "Orbi Sea Towers", PropertyType.BUILDING, 0.87, 1250.0, null);

    assertThat(match.propertyId()).isEqualTo(id);
    assertThat(match.canonicalName()).isEqualTo("Orbi Sea Towers");
    assertThat(match.type()).isEqualTo(PropertyType.BUILDING);
    assertThat(match.score()).isEqualTo(0.87);
  }

  @Test
  void distanceIsAbsentWhenTheSearchCarriedNoPoint() {
    // A text-only search has no centre to measure from, and reporting 0 would read as "right here".
    assertThat(
            new PropertyMatch(anyId(), "Orbi", PropertyType.BUILDING, 1.0, null, null).distance())
        .isEmpty();
  }

  @Test
  void distanceIsPresentWhenTheSearchCarriedAPoint() {
    assertThat(new PropertyMatch(anyId(), "Orbi", PropertyType.BUILDING, 1.0, 0.0, null).distance())
        .contains(0.0);
    assertThat(
            new PropertyMatch(anyId(), "Orbi", PropertyType.BUILDING, 1.0, 1250.0, null).distance())
        .contains(1250.0);
  }

  @Test
  void addressIsAbsentWhenThePropertyHasNoAddress() {
    PropertyMatch match =
        new PropertyMatch(anyId(), "Orbi", PropertyType.BUILDING, 1.0, null, null);

    assertThat(match.address()).isEmpty();
  }

  @Test
  void addressCarriesOnlyTheStructuredPartsThatWereKnown() {
    PropertyMatch.AddressSummary address =
        new PropertyMatch.AddressSummary("Batumi", null, "Rustaveli Street", null);

    PropertyMatch match =
        new PropertyMatch(anyId(), "Orbi", PropertyType.RESIDENTIAL_COMPLEX, 1.0, null, address);

    assertThat(match.address()).contains(address);
  }

  @Test
  void aHitAlwaysKnowsWhichPropertyItIsWhatItIsCalledAndWhatKindItIs() {
    assertThatThrownBy(
            () -> new PropertyMatch(null, "Orbi", PropertyType.BUILDING, 1.0, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new PropertyMatch(anyId(), null, PropertyType.BUILDING, 1.0, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PropertyMatch(anyId(), "Orbi", null, 1.0, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static PropertyId anyId() {
    return PropertyId.of(UUID.randomUUID());
  }
}

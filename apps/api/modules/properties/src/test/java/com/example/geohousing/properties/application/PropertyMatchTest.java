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
        new PropertyMatch(
            id,
            "Orbi Sea Towers",
            PropertyType.RESIDENTIAL_COMPLEX,
            new PropertyAddressSummary("Batumi", "Old Batumi", "Chavchavadze Avenue", "12"),
            0.87,
            1250.0);

    assertThat(match.propertyId()).isEqualTo(id);
    assertThat(match.canonicalName()).isEqualTo("Orbi Sea Towers");
    assertThat(match.type()).isEqualTo(PropertyType.RESIDENTIAL_COMPLEX);
    assertThat(match.score()).isEqualTo(0.87);
    assertThat(match.address())
        .contains(new PropertyAddressSummary("Batumi", "Old Batumi", "Chavchavadze Avenue", "12"));
  }

  @Test
  void distanceIsAbsentWhenTheSearchCarriedNoPoint() {
    // A text-only search has no centre to measure from, and reporting 0 would read as "right here".
    assertThat(hit(null).distance()).isEmpty();
  }

  @Test
  void distanceIsPresentWhenTheSearchCarriedAPoint() {
    assertThat(hit(0.0).distance()).contains(0.0);
    assertThat(hit(1250.0).distance()).contains(1250.0);
  }

  @Test
  void addressIsAbsentWhenThePropertyHasNoAddress() {
    // A property with no address row arrives as four null columns, and "no address" must stay
    // distinguishable from an address whose every part is blank so a result row can stay honest.
    assertThat(new PropertyMatch(anyId(), "Orbi", PropertyType.BUILDING, null, 1.0, null).address())
        .isEmpty();
  }

  @Test
  void aHitAlwaysKnowsWhichPropertyItIsAndWhatItIsCalled() {
    assertThatThrownBy(
            () -> new PropertyMatch(null, "Orbi", PropertyType.BUILDING, null, 1.0, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new PropertyMatch(anyId(), null, PropertyType.BUILDING, null, 1.0, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PropertyMatch(anyId(), "Orbi", null, null, 1.0, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static PropertyMatch hit(Double distanceMeters) {
    return new PropertyMatch(anyId(), "Orbi", PropertyType.BUILDING, null, 1.0, distanceMeters);
  }

  private static PropertyId anyId() {
    return PropertyId.of(UUID.randomUUID());
  }
}

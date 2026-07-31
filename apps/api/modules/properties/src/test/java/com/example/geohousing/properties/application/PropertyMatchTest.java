package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.domain.PropertyId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyMatchTest {

  @Test
  void aHitCarriesWhatItMatchedAndHowWell() {
    PropertyId id = PropertyId.of(UUID.randomUUID());

    PropertyMatch match = new PropertyMatch(id, "Orbi Sea Towers", 0.87, 1250.0);

    assertThat(match.propertyId()).isEqualTo(id);
    assertThat(match.canonicalName()).isEqualTo("Orbi Sea Towers");
    assertThat(match.score()).isEqualTo(0.87);
  }

  @Test
  void distanceIsAbsentWhenTheSearchCarriedNoPoint() {
    // A text-only search has no centre to measure from, and reporting 0 would read as "right here".
    assertThat(new PropertyMatch(anyId(), "Orbi", 1.0, null).distance()).isEmpty();
  }

  @Test
  void distanceIsPresentWhenTheSearchCarriedAPoint() {
    assertThat(new PropertyMatch(anyId(), "Orbi", 1.0, 0.0).distance()).contains(0.0);
    assertThat(new PropertyMatch(anyId(), "Orbi", 1.0, 1250.0).distance()).contains(1250.0);
  }

  @Test
  void aHitAlwaysKnowsWhichPropertyItIsAndWhatItIsCalled() {
    assertThatThrownBy(() -> new PropertyMatch(null, "Orbi", 1.0, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PropertyMatch(anyId(), null, 1.0, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static PropertyId anyId() {
    return PropertyId.of(UUID.randomUUID());
  }
}

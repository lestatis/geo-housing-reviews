package com.example.geohousing.properties.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PropertyValueObjectsTest {

  @Test
  void coordinatesRejectOutOfRangeValues() {
    assertThatCode(() -> Coordinates.of(41.71, 44.79)).doesNotThrowAnyException();
    assertThatThrownBy(() -> Coordinates.of(91, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Coordinates.of(0, 181)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addressDefaultsCountryToGeAndRequiresATwoLetterCode() {
    assertThat(new Address(null, "Tbilisi", null, null, null, null).country()).isEqualTo("GE");
    assertThat(new Address("  ", null, null, null, null, null).country()).isEqualTo("GE");
    assertThatThrownBy(() -> new Address("GEO", null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aliasRejectsBlankFieldsAndConfidenceOutOfRange() {
    assertThatCode(() -> new PropertyAlias("en", "Vake Tower", AliasSource.OFFICIAL, null))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> new PropertyAlias(" ", "Name", AliasSource.OFFICIAL, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PropertyAlias("en", "Name", AliasSource.OFFICIAL, 1.5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceRequiresATypeAndObservationTime() {
    assertThatThrownBy(() -> new PropertySource(" ", Instant.now(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PropertySource("registry", null, null))
        .isInstanceOf(NullPointerException.class);
  }
}

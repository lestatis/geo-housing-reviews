package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PseudonymFormatterTest {

  @Test
  void buildsDefaultPseudonymFromSuffix() {
    Pseudonym pseudonym = PseudonymFormatter.defaultFrom("a3f9");

    assertThat(pseudonym.value()).isEqualTo("Reviewer-a3f9");
  }

  @Test
  void rejectsBlankSuffix() {
    assertThatThrownBy(() -> PseudonymFormatter.defaultFrom("  "))
        .isInstanceOf(InvalidPseudonymException.class);
    assertThatThrownBy(() -> PseudonymFormatter.defaultFrom(null))
        .isInstanceOf(InvalidPseudonymException.class);
  }
}

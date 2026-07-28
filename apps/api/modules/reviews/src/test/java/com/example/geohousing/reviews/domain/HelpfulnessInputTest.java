package com.example.geohousing.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HelpfulnessInputTest {

  @Test
  void acceptsValuesAcrossTheWholeAllowedRange() {
    for (double value : new double[] {0.0, 0.5, 1.0}) {
      assertThat(new HelpfulnessInput(value, RankingInputVersion.V1).value()).isEqualTo(value);
    }
  }

  @Test
  void refusesAValueOutsideTheRange() {
    // The bound is what keeps helpfulness from dominating every other ranking factor, so it is
    // enforced by the type rather than trusted to each producer.
    assertThatThrownBy(() -> new HelpfulnessInput(1.0001, RankingInputVersion.V1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HelpfulnessInput(-0.0001, RankingInputVersion.V1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesNotANumber() {
    // A NaN would silently poison every comparison a ranking layer makes with it.
    assertThatThrownBy(() -> new HelpfulnessInput(Double.NaN, RankingInputVersion.V1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAValueWithoutAVersion() {
    // An unversioned value cannot be explained later, which defeats the audit requirement.
    assertThatThrownBy(() -> new HelpfulnessInput(0.5, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void theZeroInputStillCarriesItsVersion() {
    HelpfulnessInput zero = HelpfulnessInput.zero(RankingInputVersion.V1);

    assertThat(zero.value()).isZero();
    assertThat(zero.version()).isEqualTo(RankingInputVersion.V1);
  }
}

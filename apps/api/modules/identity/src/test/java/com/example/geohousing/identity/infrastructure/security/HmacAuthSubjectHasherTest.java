package com.example.geohousing.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HmacAuthSubjectHasherTest {

  private static final String PEPPER = "unit-test-pepper";
  private final HmacAuthSubjectHasher hasher = new HmacAuthSubjectHasher(PEPPER);

  @Test
  void producesLowercaseHexOfTheFixedWidthTheColumnAndCheckConstraintExpect() {
    String hash = hasher.hash("google-oauth2|1092847561");

    // V2.4's account_auth_subject_hash_format_check requires exactly this shape.
    assertThat(hash).matches("^[0-9a-f]{64}$");
  }

  @Test
  void isDeterministicForTheSameSubjectAndPepper() {
    assertThat(hasher.hash("issuer|subject")).isEqualTo(hasher.hash("issuer|subject"));
  }

  @Test
  void differentSubjectsProduceDifferentHashes() {
    assertThat(hasher.hash("issuer|a")).isNotEqualTo(hasher.hash("issuer|b"));
  }

  @Test
  void differentPeppersProduceDifferentHashesForTheSameSubject() {
    HmacAuthSubjectHasher other = new HmacAuthSubjectHasher("a-different-pepper");

    assertThat(hasher.hash("issuer|subject")).isNotEqualTo(other.hash("issuer|subject"));
  }

  @Test
  void rejectsABlankPepperAtConstructionSoTheContextFailsFast() {
    assertThatThrownBy(() -> new HmacAuthSubjectHasher(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HmacAuthSubjectHasher(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABlankSubject() {
    assertThatThrownBy(() -> hasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}

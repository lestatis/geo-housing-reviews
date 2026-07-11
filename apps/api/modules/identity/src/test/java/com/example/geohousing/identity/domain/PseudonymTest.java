package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PseudonymTest {

  @Test
  void acceptsValidValues() {
    assertThatCode(() -> new Pseudonym("Reviewer-a3f9")).doesNotThrowAnyException();
    assertThatCode(() -> new Pseudonym("abc")).doesNotThrowAnyException();
    assertThatCode(() -> new Pseudonym("Anna K")).doesNotThrowAnyException();
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> new Pseudonym(null)).isInstanceOf(InvalidPseudonymException.class);
  }

  @Test
  void rejectsTooShort() {
    assertThatThrownBy(() -> new Pseudonym("ab")).isInstanceOf(InvalidPseudonymException.class);
  }

  @Test
  void rejectsTooLong() {
    assertThatThrownBy(() -> new Pseudonym("a".repeat(33)))
        .isInstanceOf(InvalidPseudonymException.class);
  }

  @Test
  void rejectsDisallowedCharacters() {
    assertThatThrownBy(() -> new Pseudonym("bad/name"))
        .isInstanceOf(InvalidPseudonymException.class);
  }

  @Test
  void rejectsLeadingOrTrailingSeparator() {
    assertThatThrownBy(() -> new Pseudonym("-name")).isInstanceOf(InvalidPseudonymException.class);
    assertThatThrownBy(() -> new Pseudonym("name-")).isInstanceOf(InvalidPseudonymException.class);
    assertThatThrownBy(() -> new Pseudonym(" name")).isInstanceOf(InvalidPseudonymException.class);
  }

  @Test
  void toStringReturnsRawValue() {
    assertThat(new Pseudonym("Reviewer-a3f9")).hasToString("Reviewer-a3f9");
  }
}

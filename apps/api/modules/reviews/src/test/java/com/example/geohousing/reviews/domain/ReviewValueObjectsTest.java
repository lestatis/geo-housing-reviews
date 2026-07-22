package com.example.geohousing.reviews.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewValueObjectsTest {

  @Test
  void aResidencePeriodCannotEndBeforeItStartsButOpenEndsAreFine() {
    assertThatCode(() -> ResidencePeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1)))
        .doesNotThrowAnyException();
    assertThatCode(() -> ResidencePeriod.of(LocalDate.of(2024, 1, 1), null))
        .doesNotThrowAnyException();
    assertThatCode(() -> ResidencePeriod.of(null, null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> ResidencePeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aCategoryRatingIsEitherNotApplicableOrRatedInRange() {
    assertThatCode(() -> CategoryRating.rated("NOISE", 4, "thin walls")).doesNotThrowAnyException();
    assertThatCode(() -> CategoryRating.notApplicable("PARKING", null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> new CategoryRating("LIFT", 3, true, null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CategoryRating("HEATING", null, false, null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CategoryRating.rated("SECURITY", 9, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CategoryRating.rated("  ", 3, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aReviewVersionRejectsBlankBodyAndDuplicateCategories() {
    assertThatThrownBy(() -> version("   ", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                version(
                    "fine",
                    List.of(
                        CategoryRating.rated("NOISE", 4, null),
                        CategoryRating.rated("NOISE", 2, null))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ReviewVersion version(String body, List<CategoryRating> ratings) {
    return new ReviewVersion(
        UUID.randomUUID(),
        1,
        "en",
        body,
        null,
        null,
        Recommendation.NEUTRAL,
        ratings,
        null,
        Instant.parse("2026-07-20T10:00:00Z"));
  }
}

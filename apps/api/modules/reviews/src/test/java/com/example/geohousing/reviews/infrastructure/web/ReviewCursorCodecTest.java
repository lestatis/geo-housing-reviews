package com.example.geohousing.reviews.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.application.ReviewCursor;
import com.example.geohousing.reviews.domain.ReviewId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewCursorCodecTest {

  private static String encoded(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void aCursorSurvivesTheRoundTripExactly() {
    // Sub-second precision matters: publication times come from the database, and a cursor that
    // rounded would re-show or skip a review at the page boundary.
    ReviewCursor cursor =
        new ReviewCursor(
            Instant.parse("2026-07-20T10:00:00.123456Z"), ReviewId.of(UUID.randomUUID()));

    assertThat(ReviewCursorCodec.decode(ReviewCursorCodec.encode(cursor))).isEqualTo(cursor);
  }

  @Test
  void noCursorMeansTheFirstPage() {
    assertThat(ReviewCursorCodec.decode(null)).isNull();
    assertThat(ReviewCursorCodec.decode("  ")).isNull();
  }

  @Test
  void anEncodedCursorDoesNotLeakItsStructureInTheUrl() {
    String encoded =
        ReviewCursorCodec.encode(
            new ReviewCursor(
                Instant.parse("2026-07-20T10:00:00Z"), ReviewId.of(UUID.randomUUID())));

    // URL-safe and opaque: nothing a client would be tempted to construct or parse itself.
    assertThat(encoded).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void aMangledCursorIsRejectedRatherThanSilentlyRestartingTheListing() {
    // Silently returning the first page would send a paging client round in circles.
    assertThatThrownBy(() -> ReviewCursorCodec.decode("not-base64!!"))
        .isInstanceOf(InvalidReviewCursorException.class);
    assertThatThrownBy(() -> ReviewCursorCodec.decode(encoded("no-separator")))
        .isInstanceOf(InvalidReviewCursorException.class);
    assertThatThrownBy(() -> ReviewCursorCodec.decode(encoded("not-a-time|" + UUID.randomUUID())))
        .isInstanceOf(InvalidReviewCursorException.class);
    assertThatThrownBy(() -> ReviewCursorCodec.decode(encoded("2026-07-20T10:00:00Z|not-a-uuid")))
        .isInstanceOf(InvalidReviewCursorException.class);
    assertThatThrownBy(() -> ReviewCursorCodec.decode(encoded("|")))
        .isInstanceOf(InvalidReviewCursorException.class);
  }
}

package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.ReviewCursor;
import com.example.geohousing.reviews.domain.ReviewId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes a listing position as the opaque {@code nextCursor} string clients echo back
 * (API_GUIDELINES: cursor pagination with stable sort semantics).
 *
 * <p>Opaque, not secret: it holds a publication timestamp and a review id that the same client just
 * received in the page above it, so there is nothing to protect — but keeping it opaque stops
 * clients building their own and depending on the sort's internals.
 *
 * <p>Decoding is strict and total. A cursor arrives from the network, so anything unparseable is a
 * bad request, never an exception escaping as a 500 and never a silent fall back to the first page,
 * which would quietly restart a client's pagination loop.
 */
final class ReviewCursorCodec {

  private static final String SEPARATOR = "|";

  private ReviewCursorCodec() {}

  static String encode(ReviewCursor cursor) {
    String raw = cursor.publishedAt().toString() + SEPARATOR + cursor.reviewId().value();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Decodes a client-supplied cursor, or null when none was supplied. */
  static ReviewCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    String raw;
    try {
      raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new InvalidReviewCursorException();
    }

    int separator = raw.lastIndexOf(SEPARATOR);
    if (separator < 0) {
      throw new InvalidReviewCursorException();
    }
    try {
      Instant publishedAt = Instant.parse(raw.substring(0, separator));
      UUID reviewId = UUID.fromString(raw.substring(separator + SEPARATOR.length()));
      return new ReviewCursor(publishedAt, ReviewId.of(reviewId));
    } catch (RuntimeException exception) {
      throw new InvalidReviewCursorException();
    }
  }
}

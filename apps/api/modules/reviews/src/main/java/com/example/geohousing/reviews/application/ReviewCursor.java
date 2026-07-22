package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.ReviewId;
import java.time.Instant;
import java.util.Objects;

/**
 * Position in a published-review listing: the last item a caller already saw. The sort is
 * publication time descending with the review id as tie-breaker, so paging stays stable even when
 * several reviews are published in the same instant and when new reviews are published mid-listing.
 *
 * <p>This is the internal form. Encoding it into the opaque {@code nextCursor} string clients see
 * is the web layer's job (API_GUIDELINES: cursor pagination for reviews).
 */
public record ReviewCursor(Instant publishedAt, ReviewId reviewId) {

  public ReviewCursor {
    Objects.requireNonNull(publishedAt, "publishedAt");
    Objects.requireNonNull(reviewId, "reviewId");
  }
}

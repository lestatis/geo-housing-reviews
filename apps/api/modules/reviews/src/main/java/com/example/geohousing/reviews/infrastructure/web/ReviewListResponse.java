package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.ReviewPage;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.List;
import java.util.Map;

/**
 * One page of reviews. {@code nextCursor} is null on the last page — a client stops when it is
 * absent rather than when the page looks short, which is what keeps paging correct if a page ends
 * exactly on the boundary.
 */
public record ReviewListResponse(List<ReviewResponse> items, String nextCursor) {

  /**
   * @param helpfulCounts active totals for the reviews on this page, fetched in one query; a review
   *     absent from the map has no active signals
   */
  static ReviewListResponse from(ReviewPage page, Map<ReviewId, Long> helpfulCounts) {
    return new ReviewListResponse(
        page.reviews().stream()
            .map(review -> ReviewResponse.from(review, helpfulCounts.getOrDefault(review.id(), 0L)))
            .toList(),
        page.next().map(ReviewCursorCodec::encode).orElse(null));
  }
}

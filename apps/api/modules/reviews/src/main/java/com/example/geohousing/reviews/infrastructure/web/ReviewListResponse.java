package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.ReviewPage;
import java.util.List;

/**
 * One page of reviews. {@code nextCursor} is null on the last page — a client stops when it is
 * absent rather than when the page looks short, which is what keeps paging correct if a page ends
 * exactly on the boundary.
 */
public record ReviewListResponse(List<ReviewResponse> items, String nextCursor) {

  static ReviewListResponse from(ReviewPage page) {
    return new ReviewListResponse(
        page.reviews().stream().map(ReviewResponse::from).toList(),
        page.next().map(ReviewCursorCodec::encode).orElse(null));
  }
}

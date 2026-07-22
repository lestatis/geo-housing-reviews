package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Objects;

/** Reads reviews, applying the visibility rules a viewer is entitled to. */
public final class ReviewQueryService {

  /** Applied when the caller asks for nothing specific. */
  public static final int DEFAULT_PAGE_SIZE = 20;

  /** Hard cap, so a client cannot pull every review of a property in one call. */
  public static final int MAX_PAGE_SIZE = 50;

  private final ReviewRepository reviewRepository;

  public ReviewQueryService(ReviewRepository reviewRepository) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
  }

  /**
   * A single review, if the viewer is entitled to see it.
   *
   * @throws com.example.geohousing.reviews.domain.ReviewNotFoundException if it does not exist or
   *     is not visible to this viewer
   */
  public Review getById(ReviewId reviewId, ReviewViewer viewer) {
    Objects.requireNonNull(reviewId, "reviewId");
    Objects.requireNonNull(viewer, "viewer");
    return ReviewVisibility.requireVisible(reviewRepository.findById(reviewId), reviewId, viewer);
  }

  /**
   * Published reviews of a property, newest publication first. This listing is the public one: it
   * never includes drafts, reviews awaiting moderation, or hidden reviews, for any viewer —
   * moderators use the moderation queue rather than the public listing.
   *
   * <p>The requested page size is clamped to {@code [1, MAX_PAGE_SIZE]}.
   */
  public ReviewPage listPublished(PropertyRef propertyRef, ReviewCursor after, Integer pageSize) {
    Objects.requireNonNull(propertyRef, "propertyRef");
    int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    return reviewRepository.findPublishedByProperty(
        propertyRef, after, Math.clamp(size, 1, MAX_PAGE_SIZE));
  }
}

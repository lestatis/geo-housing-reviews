package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Optional;

/** Application port for persisting and reading reviews without exposing persistence details. */
public interface ReviewRepository {

  Optional<Review> findById(ReviewId reviewId);

  /**
   * The author's review of this property that still occupies the one-live-review slot, if any.
   * Mirrors the partial unique index from {@code V4.1}: {@code REJECTED} and {@code REMOVED}
   * reviews are excluded, so an author whose review was rejected can start a fresh one.
   */
  Optional<Review> findLiveByAuthorAndProperty(AuthorId authorId, PropertyRef propertyRef);

  /** Persists a new review with its first content version atomically. */
  void create(Review review);

  /**
   * Persists changes to an existing review — appended versions and status included — atomically.
   * The expected version is the one the aggregate was loaded at.
   *
   * @throws com.example.geohousing.reviews.domain.ReviewVersionConflictException if the stored
   *     version no longer matches the loaded one
   */
  void save(Review review);

  /**
   * Published reviews of a property, newest publication first, ties broken by review id so the
   * order is stable across pages. {@code after} continues from a previous page.
   */
  ReviewPage findPublishedByProperty(PropertyRef propertyRef, ReviewCursor after, int limit);
}

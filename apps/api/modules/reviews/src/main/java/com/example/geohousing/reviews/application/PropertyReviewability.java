package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.PropertyRef;
import java.util.Objects;

/**
 * What the reviews module needs to know about a property before accepting a review of it.
 *
 * @param reviewTarget the property the review must attach to. When the requested property was
 *     merged into another, this is the surviving one — so reviews of the same building keep landing
 *     on one record and the one-live-review rule stays meaningful.
 * @param acceptsNewReviews whether a new review may be submitted right now. A property withheld by
 *     an administrator still shows its existing reviews but takes no new ones.
 */
public record PropertyReviewability(PropertyRef reviewTarget, boolean acceptsNewReviews) {

  public PropertyReviewability {
    Objects.requireNonNull(reviewTarget, "reviewTarget");
  }
}

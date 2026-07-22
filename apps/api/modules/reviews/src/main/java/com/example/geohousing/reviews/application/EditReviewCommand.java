package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Objects;

/**
 * Request to replace a review's content with a new version. {@code editReason} is required: it is
 * what a moderator reads when the same review comes back for re-checking.
 */
public record EditReviewCommand(
    ReviewId reviewId, AuthorId editorId, ReviewContent content, String editReason) {

  public EditReviewCommand {
    Objects.requireNonNull(reviewId, "reviewId");
    Objects.requireNonNull(editorId, "editorId");
    Objects.requireNonNull(content, "content");
    if (editReason == null || editReason.isBlank()) {
      throw new IllegalArgumentException("editReason must not be blank");
    }
  }
}

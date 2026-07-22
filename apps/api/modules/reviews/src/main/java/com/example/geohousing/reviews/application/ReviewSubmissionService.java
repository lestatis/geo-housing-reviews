package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.IllegalReviewStateTransitionException;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Submits and edits reviews on behalf of their authors.
 *
 * <p>A submission goes straight to {@code PENDING_MODERATION}: pre-publication moderation is the
 * MVP default (docs/MODERATION.md), so nothing reaches the public listing without being checked.
 * The author keeps one live review per property — a second attempt is refused and points at the
 * existing review, which the author edits instead.
 */
public final class ReviewSubmissionService {

  private final ReviewRepository reviewRepository;
  private final PropertyLookup propertyLookup;
  private final Clock clock;

  public ReviewSubmissionService(
      ReviewRepository reviewRepository, PropertyLookup propertyLookup, Clock clock) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.propertyLookup = Objects.requireNonNull(propertyLookup, "propertyLookup");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Creates a review with its first content version and submits it for moderation.
   *
   * @throws PropertyNotFoundForReviewException if the property does not exist
   * @throws PropertyNotReviewableException if the property takes no new reviews
   * @throws DuplicateReviewException if the author already has a live review of the property
   */
  public Review submit(SubmitReviewCommand command) {
    Objects.requireNonNull(command, "command");

    PropertyReviewability reviewability =
        propertyLookup
            .findReviewability(command.propertyRef())
            .orElseThrow(() -> new PropertyNotFoundForReviewException(command.propertyRef()));
    if (!reviewability.acceptsNewReviews()) {
      throw new PropertyNotReviewableException(command.propertyRef());
    }
    // The review attaches to the surviving property, which differs from the requested one when
    // the property has been merged.
    PropertyRef target = reviewability.reviewTarget();

    reviewRepository
        .findLiveByAuthorAndProperty(command.authorId(), target)
        .ifPresent(
            existing -> {
              throw new DuplicateReviewException(target, existing.id());
            });

    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            target,
            command.authorId(),
            command.relationshipType(),
            command.residencePeriod(),
            clock);
    appendContent(review, command.content(), null);
    review.submit(clock);
    reviewRepository.create(review);
    return review;
  }

  /**
   * Replaces a review's content with a new version. Only the author may edit; editing a published
   * review returns it to moderation before it is public again.
   *
   * @throws com.example.geohousing.reviews.domain.ReviewNotFoundException if the review does not
   *     exist or the editor may not see it
   * @throws ReviewAccessDeniedException if the editor is not the author
   * @throws com.example.geohousing.reviews.domain.IllegalReviewStateTransitionException if the
   *     review is in a state its author cannot edit
   */
  public Review edit(EditReviewCommand command) {
    Objects.requireNonNull(command, "command");

    Review review =
        ReviewVisibility.requireVisible(
            reviewRepository.findById(command.reviewId()),
            command.reviewId(),
            ReviewViewer.user(command.editorId()));
    if (!review.authorId().equals(command.editorId())) {
      throw new ReviewAccessDeniedException(command.reviewId());
    }
    requireAuthorEditable(review);

    appendContent(review, command.content(), command.editReason());
    reviewRepository.save(review);
    return review;
  }

  private void appendContent(Review review, ReviewContent content, String editReason) {
    review.appendVersion(
        content.locale(),
        content.body(),
        content.pros(),
        content.cons(),
        content.recommendation(),
        content.ratings(),
        editReason,
        clock);
  }

  /**
   * A hidden review is withheld by a moderator: letting its author quietly rewrite it would turn
   * moderation into a negotiation the moderator cannot see. Appeals go through the moderation
   * module instead. Terminal states are refused by the aggregate itself.
   */
  private static void requireAuthorEditable(Review review) {
    if (review.status() == ReviewStatus.HIDDEN) {
      throw new IllegalReviewStateTransitionException(
          "a HIDDEN review cannot be edited by its author");
    }
  }
}

package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.EditReviewCommand;
import com.example.geohousing.reviews.application.ReviewContent;
import com.example.geohousing.reviews.application.ReviewQueryService;
import com.example.geohousing.reviews.application.ReviewSubmissionService;
import com.example.geohousing.reviews.application.SubmitReviewCommand;
import com.example.geohousing.reviews.domain.CategoryRating;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.ResidencePeriod;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public review endpoints: submitting a review of a property, reading one, editing one, and the
 * cursor-paginated listing of a property's published reviews.
 *
 * <p>Submitting is deliberately not a "create draft then publish" pair — a submitted review goes
 * straight to moderation, and only moderation publishes it.
 */
@RestController
class ReviewController {

  private final ReviewSubmissionService submissionService;
  private final ReviewQueryService queryService;

  ReviewController(ReviewSubmissionService submissionService, ReviewQueryService queryService) {
    this.submissionService = submissionService;
    this.queryService = queryService;
  }

  /**
   * Submits a review of a property. Responds 201 with the review awaiting moderation.
   *
   * <p>Replaying this request is safe without an idempotency key: an author holds one live review
   * per property, so a repeat is refused with 409 {@code REVIEW_ALREADY_EXISTS} carrying the
   * existing review's id and a {@code Location} header pointing at it, which is what a client that
   * lost the first response needs in order to recover.
   */
  @PostMapping("/api/properties/{propertyId}/reviews")
  ResponseEntity<ReviewResponse> submit(
      Principal principal,
      @PathVariable("propertyId") String propertyId,
      @RequestBody SubmitReviewRequest request) {
    SubmitReviewCommand command =
        new SubmitReviewCommand(
            PropertyRef.of(parseUuid(propertyId)),
            WebAuthentication.authorId(principal),
            parseRelationshipType(request.relationshipType()),
            residencePeriod(request.residenceFrom(), request.residenceTo()),
            content(
                request.locale(),
                request.body(),
                request.pros(),
                request.cons(),
                request.recommendation(),
                request.ratings()));

    Review review = submissionService.submit(command);
    return ResponseEntity.created(location(review.id())).body(ReviewResponse.from(review));
  }

  @GetMapping("/api/reviews/{reviewId}")
  ReviewResponse get(Principal principal, @PathVariable("reviewId") String reviewId) {
    return ReviewResponse.from(
        queryService.getById(
            ReviewId.of(parseUuid(reviewId)), WebAuthentication.publicViewer(principal)));
  }

  /** Replaces a review's content with a new version. Only its author may edit. */
  @PutMapping("/api/reviews/{reviewId}")
  ReviewResponse edit(
      Principal principal,
      @PathVariable("reviewId") String reviewId,
      @RequestBody EditReviewRequest request) {
    EditReviewCommand command =
        new EditReviewCommand(
            ReviewId.of(parseUuid(reviewId)),
            WebAuthentication.authorId(principal),
            content(
                request.locale(),
                request.body(),
                request.pros(),
                request.cons(),
                request.recommendation(),
                request.ratings()),
            request.editReason());
    return ReviewResponse.from(submissionService.edit(command));
  }

  /**
   * A property's published reviews, newest publication first. Unpublished reviews never appear
   * here, not even the caller's own: this is the public listing.
   */
  @GetMapping("/api/properties/{propertyId}/reviews")
  ReviewListResponse list(
      @PathVariable("propertyId") String propertyId,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "limit", required = false) Integer limit) {
    return ReviewListResponse.from(
        queryService.listPublished(
            PropertyRef.of(parseUuid(propertyId)), ReviewCursorCodec.decode(cursor), limit));
  }

  private static URI location(ReviewId reviewId) {
    return URI.create("/api/reviews/" + reviewId.value());
  }

  /** A malformed UUID throws {@link IllegalArgumentException}, mapped to 400 by the advice. */
  private static UUID parseUuid(String value) {
    return UUID.fromString(value);
  }

  private static RelationshipType parseRelationshipType(String value) {
    if (value == null) {
      throw new IllegalArgumentException("relationshipType is required");
    }
    try {
      return RelationshipType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown relationshipType: " + value);
    }
  }

  private static Recommendation parseRecommendation(String value) {
    if (value == null) {
      throw new IllegalArgumentException("recommendation is required");
    }
    try {
      return Recommendation.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown recommendation: " + value);
    }
  }

  private static ResidencePeriod residencePeriod(LocalDate from, LocalDate to) {
    return from == null && to == null ? null : ResidencePeriod.of(from, to);
  }

  private static ReviewContent content(
      String locale,
      String body,
      String pros,
      String cons,
      String recommendation,
      List<SubmitReviewRequest.CategoryRatingRequest> ratings) {
    return new ReviewContent(
        locale,
        body,
        pros,
        cons,
        parseRecommendation(recommendation),
        ratings == null ? List.of() : ratings.stream().map(ReviewController::toRating).toList());
  }

  private static CategoryRating toRating(SubmitReviewRequest.CategoryRatingRequest rating) {
    boolean notApplicable = Boolean.TRUE.equals(rating.notApplicable());
    return new CategoryRating(rating.category(), rating.value(), notApplicable, rating.note(), 1);
  }
}

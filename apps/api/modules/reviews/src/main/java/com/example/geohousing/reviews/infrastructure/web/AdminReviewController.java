package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.HelpfulSignalQueryService;
import com.example.geohousing.reviews.application.ReviewModerationService;
import com.example.geohousing.reviews.application.ReviewQueryService;
import com.example.geohousing.reviews.application.ReviewViewer;
import com.example.geohousing.reviews.domain.ModeratorId;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moderation actions on reviews. The {@code ROLE_ADMIN} gate is enforced by the security filter
 * chain ({@code /api/admin/**}), so reaching this controller already implies an authenticated
 * admin; every action is audited by {@link ReviewModerationService}, including attempts against a
 * review that does not exist.
 *
 * <p>This is where elevated visibility lives — the moderator viewer on {@code GET} is scoped to
 * this admin path, and every state change leaves an audit row, unlike the public endpoints, which
 * grant admins nothing extra.
 */
@RestController
@RequestMapping("/api/admin/reviews")
class AdminReviewController {

  private final ReviewModerationService moderationService;
  private final ReviewQueryService queryService;
  private final HelpfulSignalQueryService helpfulSignalQueryService;

  AdminReviewController(
      ReviewModerationService moderationService,
      ReviewQueryService queryService,
      HelpfulSignalQueryService helpfulSignalQueryService) {
    this.moderationService = moderationService;
    this.queryService = queryService;
    this.helpfulSignalQueryService = helpfulSignalQueryService;
  }

  /** A single review as a moderator sees it: unpublished states included. */
  @GetMapping("/{reviewId}")
  ReviewResponse get(Principal principal, @PathVariable("reviewId") String reviewId) {
    Review review =
        queryService.getById(
            parseId(reviewId), ReviewViewer.moderator(WebAuthentication.authorId(principal)));
    return withHelpfulCount(review);
  }

  @PostMapping("/{reviewId}/publish")
  ReviewResponse publish(
      Principal principal,
      @PathVariable("reviewId") String reviewId,
      @RequestBody ModerationDecisionRequest request) {
    ReviewId id = parseId(reviewId);
    return respond(
        () ->
            moderationService.publish(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  @PostMapping("/{reviewId}/reject")
  ReviewResponse reject(
      Principal principal,
      @PathVariable("reviewId") String reviewId,
      @RequestBody ModerationDecisionRequest request) {
    ReviewId id = parseId(reviewId);
    return respond(
        () ->
            moderationService.reject(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  @PostMapping("/{reviewId}/hide")
  ReviewResponse hide(
      Principal principal,
      @PathVariable("reviewId") String reviewId,
      @RequestBody ModerationDecisionRequest request) {
    ReviewId id = parseId(reviewId);
    return respond(
        () ->
            moderationService.hide(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  @PostMapping("/{reviewId}/restore")
  ReviewResponse restore(
      Principal principal,
      @PathVariable("reviewId") String reviewId,
      @RequestBody ModerationDecisionRequest request) {
    ReviewId id = parseId(reviewId);
    return respond(
        () ->
            moderationService.restore(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  @PostMapping("/{reviewId}/remove")
  ReviewResponse remove(
      Principal principal,
      @PathVariable("reviewId") String reviewId,
      @RequestBody ModerationDecisionRequest request) {
    ReviewId id = parseId(reviewId);
    return respond(
        () ->
            moderationService.remove(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  private static ModeratorId moderator(Principal principal) {
    return ModeratorId.of(UUID.fromString(principal.getName()));
  }

  private static ReviewId parseId(String value) {
    // A malformed UUID throws IllegalArgumentException, mapped to 400 by the reviews advice.
    return ReviewId.of(UUID.fromString(value));
  }

  private static long requiredVersion(ModerationDecisionRequest request) {
    if (request.version() == null) {
      throw new IllegalArgumentException("version is required: it is the review the moderator saw");
    }
    return request.version();
  }

  private ReviewResponse respond(Supplier<Optional<Review>> action, ReviewId requestedId) {
    // In the admin context "not found" is honest — there is nothing to protect about an id that
    // has no review — and the attempt has already been audited by the service.
    return action
        .get()
        .map(this::withHelpfulCount)
        .orElseThrow(() -> new ReviewNotFoundException(requestedId));
  }

  /**
   * A moderator sees the same aggregate a reader does — how many people found the review helpful,
   * never who. Signals only exist on published reviews, so anything else counts as zero without a
   * query.
   */
  private ReviewResponse withHelpfulCount(Review review) {
    long helpfulCount =
        review.status() == ReviewStatus.PUBLISHED
            ? helpfulSignalQueryService
                .activeCountsForVisibleReviews(List.of(review.id()))
                .getOrDefault(review.id(), 0L)
            : 0L;
    return ReviewResponse.from(review, helpfulCount);
  }
}

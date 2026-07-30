package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.api.ModeratableReview;
import com.example.geohousing.reviews.api.ReviewModerationConflictException;
import com.example.geohousing.reviews.api.ReviewModerationEffect;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.IllegalReviewStateTransitionException;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewModerationAuditEvent;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewModerationGatewayAdapterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReviewRepository reviews = new InMemoryReviewRepository();
  private final RecordingModerationRepository moderationRepository =
      new RecordingModerationRepository(reviews);
  private final ReviewModerationGatewayAdapter gateway =
      new ReviewModerationGatewayAdapter(
          reviews, new ReviewModerationService(reviews, moderationRepository, CLOCK));

  @Test
  void itTellsAModeratorWhoWroteTheReviewAndWhichVersionTheyAreLookingAt() {
    Review review = published();

    ModeratableReview found = gateway.find(review.id().value()).orElseThrow();

    assertThat(found.reviewId()).isEqualTo(review.id().value());
    assertThat(found.authorAccountId()).isEqualTo(review.authorId().value());
    assertThat(found.version()).isEqualTo(review.version());
    assertThat(found.published()).isTrue();
  }

  @Test
  void itCarriesNoReviewContentAcrossTheBoundary() {
    // The contract has exactly these four components. Adding the review's text would put the same
    // sensitive material in a second module under a second set of access rules — a moderator who
    // needs to read it uses this module's own audited admin endpoint.
    assertThat(ModeratableReview.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactlyInAnyOrder("reviewId", "authorAccountId", "version", "published");
  }

  @Test
  void anUnpublishedReviewIsReportedAsSuch() {
    assertThat(gateway.find(pending().id().value()).orElseThrow().published()).isFalse();
  }

  @Test
  void aReviewThatDoesNotExistIsSimplyAbsent() {
    assertThat(gateway.find(UUID.randomUUID())).isEmpty();
  }

  @Test
  void hidingWithdrawsAPublishedReviewAndIsAudited() {
    Review review = published();

    boolean applied = apply(review, ReviewModerationEffect.HIDE, "PRIVACY_RISK");

    assertThat(applied).isTrue();
    assertThat(statusOf(review)).isEqualTo(ReviewStatus.HIDDEN);
    assertThat(moderationRepository.applied).isEqualTo(1);
  }

  @Test
  void eachEffectReachesTheCorrespondingTransition() {
    // Each is exercised from a state that allows it. The switch over the published enum is
    // exhaustive, so a new constant fails compilation rather than falling through silently.
    Review toPublish = pending();
    assertThat(apply(toPublish, ReviewModerationEffect.PUBLISH, "CLEAN")).isTrue();
    assertThat(statusOf(toPublish)).isEqualTo(ReviewStatus.PUBLISHED);

    Review toReject = pending();
    assertThat(apply(toReject, ReviewModerationEffect.REJECT, "SPAM")).isTrue();
    assertThat(statusOf(toReject)).isEqualTo(ReviewStatus.REJECTED);

    Review toRemove = published();
    assertThat(apply(toRemove, ReviewModerationEffect.REMOVE, "DOXXING")).isTrue();
    assertThat(statusOf(toRemove)).isEqualTo(ReviewStatus.REMOVED);

    Review toRestore = published();
    apply(toRestore, ReviewModerationEffect.HIDE, "PRIVACY_RISK");
    assertThat(apply(toRestore, ReviewModerationEffect.RESTORE, "RESOLVED")).isTrue();
    assertThat(statusOf(toRestore)).isEqualTo(ReviewStatus.PUBLISHED);
  }

  @Test
  void anOverturnedTakedownBringsTheReviewBack() {
    Review review = published();
    apply(review, ReviewModerationEffect.REMOVE, "DOXXING");
    assertThat(statusOf(review)).isEqualTo(ReviewStatus.REMOVED);

    boolean applied = apply(review, ReviewModerationEffect.REINSTATE, "APPEAL_UPHELD");

    // Without this, a takedown demand that succeeds and then loses on appeal still gets what it
    // wanted. The audit carries the reinstatement like any other action.
    assertThat(applied).isTrue();
    assertThat(statusOf(review)).isEqualTo(ReviewStatus.PUBLISHED);
    assertThat(moderationRepository.applied).isEqualTo(2);
  }

  @Test
  void reinstatementCannotStandInForAnOrdinaryPublish() {
    Review awaitingModeration = pending();

    assertThatThrownBy(() -> apply(awaitingModeration, ReviewModerationEffect.REINSTATE, "X"))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
  }

  @Test
  void aStaleVersionIsRefusedAsThePublishedConflictNotTheDomainOne() {
    Review review = published();

    // Callers must not have to catch this module's internal exception type.
    assertThatThrownBy(
            () ->
                gateway.apply(
                    review.id().value(),
                    ReviewModerationEffect.REMOVE,
                    currentVersionOf(review) + 5,
                    UUID.randomUUID(),
                    "DOXXING"))
        .isInstanceOf(ReviewModerationConflictException.class);

    assertThat(statusOf(review)).isEqualTo(ReviewStatus.PUBLISHED);
  }

  @Test
  void anEffectOnAReviewThatDoesNotExistIsReportedRatherThanThrown() {
    boolean applied =
        gateway.apply(
            UUID.randomUUID(), ReviewModerationEffect.REMOVE, 0L, UUID.randomUUID(), "DOXXING");

    // Still audited: an action against missing content leaves a trace.
    assertThat(applied).isFalse();
    assertThat(moderationRepository.attempts).isEqualTo(1);
  }

  @Test
  void anActionWithoutAReasonCodeIsRefused() {
    Review review = published();

    assertThatThrownBy(() -> apply(review, ReviewModerationEffect.REMOVE, "  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(statusOf(review)).isEqualTo(ReviewStatus.PUBLISHED);
  }

  private boolean apply(Review review, ReviewModerationEffect effect, String reasonCode) {
    return gateway.apply(
        review.id().value(), effect, currentVersionOf(review), UUID.randomUUID(), reasonCode);
  }

  private long currentVersionOf(Review review) {
    return reviews.findById(review.id()).orElseThrow().version();
  }

  private ReviewStatus statusOf(Review review) {
    return reviews.findById(review.id()).orElseThrow().status();
  }

  private Review pending() {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(UUID.randomUUID()),
            RelationshipType.CURRENT_RESIDENT,
            null,
            CLOCK);
    review.appendVersion(
        "ka", "ტექსტი", null, null, Recommendation.RECOMMEND, List.of(), null, CLOCK);
    review.submit(CLOCK);
    reviews.create(review);
    return review;
  }

  private Review published() {
    Review review = pending();
    review.publish(CLOCK);
    reviews.save(review);
    return review;
  }

  /**
   * Stands in for the real adapter, which commits the mutation and its audit row together. It must
   * actually save: {@link InMemoryReviewRepository} snapshots on every read, so a transition that
   * is never saved is never visible — the same rule the real repository enforces.
   */
  private static final class RecordingModerationRepository implements ReviewModerationRepository {

    private final InMemoryReviewRepository reviews;
    private int applied;
    private int attempts;

    RecordingModerationRepository(InMemoryReviewRepository reviews) {
      this.reviews = reviews;
    }

    @Override
    public void applyDecision(Review review, ReviewModerationAuditEvent event) {
      reviews.save(review);
      applied++;
    }

    @Override
    public void recordAttempt(ReviewModerationAuditEvent event) {
      attempts++;
    }
  }
}

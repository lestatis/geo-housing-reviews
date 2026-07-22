package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.IllegalReviewStateTransitionException;
import com.example.geohousing.reviews.domain.ModeratorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewModerationAction;
import com.example.geohousing.reviews.domain.ReviewModerationAuditEvent;
import com.example.geohousing.reviews.domain.ReviewModerationOutcome;
import com.example.geohousing.reviews.domain.ReviewStatus;
import com.example.geohousing.reviews.domain.ReviewVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewModerationServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  private final InMemoryReviewRepository reviews = new InMemoryReviewRepository();
  private final RecordingModerationRepository moderation = new RecordingModerationRepository();
  private final ReviewModerationService service =
      new ReviewModerationService(reviews, moderation, CLOCK);

  /**
   * Both writes recorded so the tests can assert that the mutation travelled with its audit row.
   */
  private final class RecordingModerationRepository implements ReviewModerationRepository {
    final List<ReviewModerationAuditEvent> events = new ArrayList<>();

    @Override
    public void applyDecision(Review review, ReviewModerationAuditEvent event) {
      reviews.save(review);
      events.add(event);
    }

    @Override
    public void recordAttempt(ReviewModerationAuditEvent event) {
      events.add(event);
    }
  }

  private Review storePending() {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(UUID.randomUUID()),
            RelationshipType.CURRENT_RESIDENT,
            null,
            CLOCK);
    review.appendVersion(
        "ka", "ტექსტი", null, null, Recommendation.NEUTRAL, List.of(), null, CLOCK);
    review.submit(CLOCK);
    reviews.create(review);
    return review;
  }

  private ReviewModerationAuditEvent lastEvent() {
    return moderation.events.get(moderation.events.size() - 1);
  }

  @Test
  void publishingAppliesTheTransitionAndAuditsTheJudgedContentVersion() {
    Review review = storePending();
    UUID contentVersionId = review.currentVersion().orElseThrow().id();

    Review published =
        service.publish(MODERATOR, review.id(), review.version(), "CLEAN").orElseThrow();

    assertThat(published.status()).isEqualTo(ReviewStatus.PUBLISHED);
    assertThat(published.publishedAt()).contains(CLOCK.instant());
    assertThat(reviews.findById(review.id()).orElseThrow().status())
        .isEqualTo(ReviewStatus.PUBLISHED);

    ReviewModerationAuditEvent event = lastEvent();
    assertThat(event.action()).isEqualTo(ReviewModerationAction.PUBLISH);
    assertThat(event.outcome()).isEqualTo(ReviewModerationOutcome.APPLIED);
    assertThat(event.reviewVersionId()).contains(contentVersionId);
    assertThat(event.reasonCode()).isEqualTo("CLEAN");
    assertThat(event.moderatorId()).isEqualTo(MODERATOR);
  }

  @Test
  void rejectingIsTerminalAndFreesTheAuthorsSlot() {
    Review review = storePending();

    service.reject(MODERATOR, review.id(), review.version(), "PERSONAL_DATA").orElseThrow();

    Review stored = reviews.findById(review.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(ReviewStatus.REJECTED);
    assertThat(reviews.findLiveByAuthorAndProperty(stored.authorId(), stored.propertyRef()))
        .isEmpty();
  }

  @Test
  void hideAndRestoreRoundTripThroughTheStore() {
    Review review = storePending();
    service.publish(MODERATOR, review.id(), review.version(), "CLEAN");

    long afterPublish = reviews.findById(review.id()).orElseThrow().version();
    service.hide(MODERATOR, review.id(), afterPublish, "UNDER_DISPUTE");
    assertThat(reviews.findById(review.id()).orElseThrow().status()).isEqualTo(ReviewStatus.HIDDEN);

    long afterHide = reviews.findById(review.id()).orElseThrow().version();
    service.restore(MODERATOR, review.id(), afterHide, "DISPUTE_RESOLVED");
    assertThat(reviews.findById(review.id()).orElseThrow().status())
        .isEqualTo(ReviewStatus.PUBLISHED);

    assertThat(moderation.events)
        .extracting(ReviewModerationAuditEvent::action)
        .containsExactly(
            ReviewModerationAction.PUBLISH,
            ReviewModerationAction.HIDE,
            ReviewModerationAction.RESTORE);
  }

  @Test
  void anActionAgainstAMissingReviewIsAuditedAsNotFound() {
    ReviewId missing = ReviewId.of(UUID.randomUUID());

    assertThat(service.publish(MODERATOR, missing, 0L, "CLEAN")).isEmpty();

    ReviewModerationAuditEvent event = lastEvent();
    assertThat(event.outcome()).isEqualTo(ReviewModerationOutcome.NOT_FOUND);
    assertThat(event.reviewId()).isEqualTo(missing);
    assertThat(event.reviewVersionId()).isEmpty();
  }

  @Test
  void aStaleVersionIsRefusedBeforeAnythingChanges() {
    Review review = storePending();

    assertThatThrownBy(() -> service.publish(MODERATOR, review.id(), review.version() + 1, "CLEAN"))
        .isInstanceOf(ReviewVersionConflictException.class);

    assertThat(reviews.findById(review.id()).orElseThrow().status())
        .isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(moderation.events).isEmpty();
  }

  @Test
  void anIllegalTransitionIsRefusedAndLeavesNoAppliedAudit() {
    Review review = storePending();
    service.publish(MODERATOR, review.id(), review.version(), "CLEAN");
    long version = reviews.findById(review.id()).orElseThrow().version();

    // Publishing an already-published review is a state conflict, not a no-op.
    assertThatThrownBy(() -> service.publish(MODERATOR, review.id(), version, "CLEAN"))
        .isInstanceOf(IllegalReviewStateTransitionException.class);
    assertThat(moderation.events).hasSize(1);
  }

  @Test
  void anActionWithoutAReasonCodeIsRefusedBeforeAnyStateIsTouched() {
    Review review = storePending();

    for (String blank : new String[] {null, "", "   "}) {
      assertThatThrownBy(() -> service.reject(MODERATOR, review.id(), review.version(), blank))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(reviews.findById(review.id()).orElseThrow().status())
        .isEqualTo(ReviewStatus.PENDING_MODERATION);
    assertThat(moderation.events).isEmpty();
  }

  @Test
  void removingAnEmptyDraftAuditsWithoutAContentVersion() {
    Review draft =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(UUID.randomUUID()),
            RelationshipType.OTHER,
            null,
            CLOCK);
    reviews.create(draft);

    service.remove(MODERATOR, draft.id(), draft.version(), "SPAM_ACCOUNT").orElseThrow();

    assertThat(reviews.findById(draft.id()).orElseThrow().status()).isEqualTo(ReviewStatus.REMOVED);
    assertThat(lastEvent().reviewVersionId()).isEmpty();
  }
}

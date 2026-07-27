package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HelpfulSignalServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReviewRepository reviews = new InMemoryReviewRepository();
  private final InMemoryHelpfulSignalRepository signals = new InMemoryHelpfulSignalRepository();
  private final HelpfulSignalService service = new HelpfulSignalService(reviews, signals, CLOCK);

  @Test
  void addsAnActiveSignalToAPublishedReview() {
    Review review = storePublished(UUID.randomUUID());
    HelpfulSignalVoterId voter = HelpfulSignalVoterId.of(UUID.randomUUID());

    HelpfulSignal signal = service.add(review.id(), voter);

    assertThat(signal.isActive()).isTrue();
    assertThat(signal.reviewId()).isEqualTo(review.id());
    assertThat(signal.voterId()).isEqualTo(voter);
    assertThat(signals.findActive(review.id(), voter))
        .hasValueSatisfying(
            stored -> {
              assertThat(stored.id()).isEqualTo(signal.id());
              assertThat(stored.createdAt()).isEqualTo(signal.createdAt());
            });
  }

  @Test
  void refusesADuplicateActiveSignalWithoutCreatingAnother() {
    Review review = storePublished(UUID.randomUUID());
    HelpfulSignalVoterId voter = HelpfulSignalVoterId.of(UUID.randomUUID());
    service.add(review.id(), voter);

    assertThatThrownBy(() -> service.add(review.id(), voter))
        .isInstanceOf(HelpfulSignalAlreadyActiveException.class);
    assertThat(signals.byId).hasSize(1);
  }

  @Test
  void refusesASignalOnTheVotersOwnReview() {
    UUID author = UUID.randomUUID();
    Review review = storePublished(author);

    assertThatThrownBy(() -> service.add(review.id(), HelpfulSignalVoterId.of(author)))
        .isInstanceOf(SelfHelpfulSignalException.class);
    assertThat(signals.byId).isEmpty();
  }

  @Test
  void treatsUnpublishedReviewsAsMissingRatherThanSignalEligible() {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(UUID.randomUUID()),
            RelationshipType.OTHER,
            null,
            CLOCK);
    reviews.create(review);

    assertThatThrownBy(() -> service.add(review.id(), HelpfulSignalVoterId.of(UUID.randomUUID())))
        .isInstanceOf(ReviewNotFoundException.class);
    assertThat(review.status()).isEqualTo(ReviewStatus.DRAFT);
    assertThat(signals.byId).isEmpty();
  }

  @Test
  void withdrawsAnActiveSignalAndMakesARepeatedWithdrawalANoop() {
    Review review = storePublished(UUID.randomUUID());
    HelpfulSignalVoterId voter = HelpfulSignalVoterId.of(UUID.randomUUID());
    service.add(review.id(), voter);

    assertThat(service.withdraw(review.id(), voter)).isTrue();
    assertThat(signals.findActive(review.id(), voter)).isEmpty();
    assertThat(signals.byId.values()).allSatisfy(signal -> assertThat(signal.isActive()).isFalse());
    assertThat(service.withdraw(review.id(), voter)).isFalse();
  }

  private Review storePublished(UUID authorId) {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(authorId),
            RelationshipType.CURRENT_RESIDENT,
            null,
            CLOCK);
    review.appendVersion(
        "ka", "სასარგებლო მიმოხილვა", null, null, Recommendation.RECOMMEND, List.of(), null, CLOCK);
    review.submit(CLOCK);
    review.publish(CLOCK);
    reviews.create(review);
    return review;
  }
}

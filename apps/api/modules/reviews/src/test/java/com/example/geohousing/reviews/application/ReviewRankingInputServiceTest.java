package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.HelpfulnessInput;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.RankingInputVersion;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewRankingInputServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReviewRepository reviews = new InMemoryReviewRepository();
  private final InMemoryHelpfulSignalRepository signals = new InMemoryHelpfulSignalRepository();
  private final HelpfulSignalService commandService =
      new HelpfulSignalService(reviews, signals, CLOCK);
  private final ReviewRankingInputService rankingInputs =
      new ReviewRankingInputService(new HelpfulSignalQueryService(reviews, signals));

  @Test
  void everyRequestedReviewGetsAnInputIncludingOnesNobodyHasSignalled() {
    Review signalled = storePublished();
    Review ignored = storePublished();
    signal(signalled, 2);

    Map<ReviewId, HelpfulnessInput> inputs =
        rankingInputs.forVisibleReviews(List.of(signalled.id(), ignored.id()));

    // A gap would leave a ranking layer to invent its own default for an unsignalled review, which
    // is how two callers end up disagreeing about the same review.
    assertThat(inputs).containsOnlyKeys(signalled.id(), ignored.id());
    assertThat(inputs.get(ignored.id()).value()).isZero();
    assertThat(inputs.get(signalled.id()).value()).isPositive();
  }

  @Test
  void aMoreSignalledReviewNeverScoresBelowALessSignalledOne() {
    Review popular = storePublished();
    Review quiet = storePublished();
    signal(popular, 6);
    signal(quiet, 1);

    Map<ReviewId, HelpfulnessInput> inputs =
        rankingInputs.forVisibleReviews(List.of(popular.id(), quiet.id()));

    assertThat(inputs.get(popular.id()).value()).isGreaterThan(inputs.get(quiet.id()).value());
  }

  @Test
  void aWithdrawnSignalStopsCounting() {
    Review review = storePublished();
    HelpfulSignalVoterId voter = HelpfulSignalVoterId.of(UUID.randomUUID());
    commandService.add(review.id(), voter);
    commandService.withdraw(review.id(), voter);

    Map<ReviewId, HelpfulnessInput> inputs = rankingInputs.forVisibleReviews(List.of(review.id()));

    assertThat(inputs.get(review.id()).value()).isZero();
  }

  @Test
  void everyInputCarriesTheCurrentVersionSoItCanBeExplainedLater() {
    Review review = storePublished();
    signal(review, 3);

    Map<ReviewId, HelpfulnessInput> inputs = rankingInputs.forVisibleReviews(List.of(review.id()));

    assertThat(inputs.get(review.id()).version()).isEqualTo(RankingInputVersion.current());
  }

  @Test
  void anEmptyRequestAsksForNothing() {
    assertThat(rankingInputs.forVisibleReviews(List.of())).isEmpty();
  }

  private void signal(Review review, int voters) {
    for (int i = 0; i < voters; i++) {
      commandService.add(review.id(), HelpfulSignalVoterId.of(UUID.randomUUID()));
    }
  }

  private Review storePublished() {
    Review review =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(UUID.randomUUID()),
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

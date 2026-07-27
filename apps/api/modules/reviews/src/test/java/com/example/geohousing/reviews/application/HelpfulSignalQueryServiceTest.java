package com.example.geohousing.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HelpfulSignalQueryServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReviewRepository reviews = new InMemoryReviewRepository();
  private final InMemoryHelpfulSignalRepository signals = new InMemoryHelpfulSignalRepository();
  private final HelpfulSignalService commandService =
      new HelpfulSignalService(reviews, signals, CLOCK);
  private final HelpfulSignalQueryService queryService =
      new HelpfulSignalQueryService(reviews, signals);

  @Test
  void returnsOnlyAnActiveAggregateCountForAPublishedReview() {
    Review review = storePublished();
    commandService.add(review.id(), HelpfulSignalVoterId.of(UUID.randomUUID()));
    HelpfulSignalVoterId withdrawnVoter = HelpfulSignalVoterId.of(UUID.randomUUID());
    commandService.add(review.id(), withdrawnVoter);
    commandService.withdraw(review.id(), withdrawnVoter);

    HelpfulSignalCount count = queryService.countForPublishedReview(review.id());

    assertThat(count.reviewId()).isEqualTo(review.id());
    assertThat(count.value()).isOne();
  }

  @Test
  void treatsAnUnpublishedReviewAsMissing() {
    Review draft =
        Review.create(
            ReviewId.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            AuthorId.of(UUID.randomUUID()),
            RelationshipType.OTHER,
            null,
            CLOCK);
    reviews.create(draft);

    assertThatThrownBy(() -> queryService.countForPublishedReview(draft.id()))
        .isInstanceOf(ReviewNotFoundException.class);
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

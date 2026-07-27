package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.reviews.application.HelpfulSignalAlreadyActiveException;
import com.example.geohousing.reviews.application.HelpfulSignalCount;
import com.example.geohousing.reviews.application.HelpfulSignalQueryService;
import com.example.geohousing.reviews.application.HelpfulSignalRepository;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalId;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.Recommendation;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class HelpfulSignalPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private ReviewRepository reviews;
  @Autowired private HelpfulSignalRepository signals;
  @Autowired private HelpfulSignalQueryService counts;

  @Test
  void persistsWithdrawsAndCountsOnlyActiveSignals() {
    Review review = storePublished();
    HelpfulSignalVoterId voter = HelpfulSignalVoterId.of(UUID.randomUUID());
    HelpfulSignal signal = newSignal(review.id(), voter);

    signals.create(signal);

    assertThat(signals.findActive(review.id(), voter))
        .hasValueSatisfying(stored -> assertThat(stored.id()).isEqualTo(signal.id()));
    assertThat(signals.countActive(review.id())).isOne();
    assertThat(counts.countForPublishedReview(review.id()))
        .isEqualTo(new HelpfulSignalCount(review.id(), 1));

    HelpfulSignal stored = signals.findActive(review.id(), voter).orElseThrow();
    stored.withdraw(CLOCK);
    signals.withdraw(stored);

    assertThat(signals.findActive(review.id(), voter)).isEmpty();
    assertThat(signals.countActive(review.id())).isZero();
  }

  @Test
  void concurrentActiveSignalsLeaveOneRowAndTranslateTheUniqueConstraint() throws Exception {
    Review review = storePublished();
    HelpfulSignalVoterId voter = HelpfulSignalVoterId.of(UUID.randomUUID());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Throwable> first =
          executor.submit(() -> createConcurrently(review.id(), voter, ready, start));
      Future<Throwable> second =
          executor.submit(() -> createConcurrently(review.id(), voter, ready, start));
      ready.await();
      start.countDown();

      List<Throwable> outcomes = Arrays.asList(first.get(), second.get());
      assertThat(outcomes).filteredOn(value -> value == null).hasSize(1);
      assertThat(outcomes)
          .filteredOn(HelpfulSignalAlreadyActiveException.class::isInstance)
          .hasSize(1);
    }

    assertThat(signals.countActive(review.id())).isOne();
  }

  private Throwable createConcurrently(
      ReviewId reviewId, HelpfulSignalVoterId voter, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
      signals.create(newSignal(reviewId, voter));
      return null;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return exception;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private HelpfulSignal newSignal(ReviewId reviewId, HelpfulSignalVoterId voter) {
    return HelpfulSignal.create(HelpfulSignalId.of(UUID.randomUUID()), reviewId, voter, CLOCK);
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

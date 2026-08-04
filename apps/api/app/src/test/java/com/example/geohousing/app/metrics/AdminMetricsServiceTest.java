package com.example.geohousing.app.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.moderation.api.ModerationMetrics;
import com.example.geohousing.moderation.api.ModerationThroughput;
import com.example.geohousing.reviews.api.ReviewMetrics;
import com.example.geohousing.reviews.api.ReviewThroughput;
import com.example.geohousing.verification.api.VerificationMetrics;
import com.example.geohousing.verification.api.VerificationThroughput;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminMetricsServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
  private static final Instant FROM = NOW.minus(Duration.ofDays(30));

  /**
   * "How long has the oldest case been waiting" is measured against now, not against the window's
   * end — a question about a past month is still asking how long somebody has been waiting today.
   */
  private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void theQueuesAreReportedAsTheyStandRightNow() {
    AdminMetrics metrics = service().current(FROM, NOW);

    assertThat(metrics.openModerationCases()).isEqualTo(12);
    assertThat(metrics.reviewsAwaitingModeration()).isEqualTo(31);
    assertThat(metrics.pendingVerifications()).isEqualTo(4);
  }

  @Test
  void theOldestOpenCaseIsReportedAsAnAgeRatherThanAnInstant() {
    // How long somebody has been waiting is the actionable form. An administrator reading a
    // timestamp has to do the subtraction themselves, and will do it against the wrong timezone.
    AdminMetrics metrics = service().current(FROM, NOW);

    assertThat(metrics.oldestOpenCaseAgeDays()).isEqualTo(3);
  }

  @Test
  void anEmptyQueueHasNoOldestCaseRatherThanAnAgeOfZero() {
    // Zero would read as "something has been waiting, but not long" — the opposite of the truth.
    AdminMetricsService quiet =
        new AdminMetricsService(
            moderation(0, Optional.empty(), new ModerationThroughput(0, 0, 0)),
            reviews(0, new ReviewThroughput(0, 0)),
            verification(0, new VerificationThroughput(0, 0)),
            FIXED);

    assertThat(quiet.current(FROM, NOW).oldestOpenCaseAgeDays()).isNull();
  }

  @Test
  void theOverturnRateIsCountsFirstAndAPercentageSecond() {
    // "33%" over three appeals reads as a finding; "1 of 3" reads as what it is. The screen shows
    // both, so the counts have to survive the service rather than be reduced to a rate here.
    AdminMetrics metrics = service().current(FROM, NOW);

    assertThat(metrics.appealsHeard()).isEqualTo(14);
    assertThat(metrics.appealsOverturned()).isEqualTo(3);
  }

  @Test
  void aWindowWithNoAppealsHasNoRateRatherThanZeroPercent() {
    // Zero percent is a claim that nothing was overturned. Nothing was *heard*, which is a
    // different statement and the only honest one.
    AdminMetricsService quiet =
        new AdminMetricsService(
            moderation(0, Optional.empty(), new ModerationThroughput(0, 0, 0)),
            reviews(0, new ReviewThroughput(0, 0)),
            verification(0, new VerificationThroughput(0, 0)),
            FIXED);

    assertThat(quiet.current(FROM, NOW).appealOverturnPercentage()).isNull();
  }

  @Test
  void anOverturnRateIsRoundedForReadingRatherThanForArithmetic() {
    AdminMetrics metrics = service().current(FROM, NOW);

    // 3 of 14 is 21.4%.
    assertThat(metrics.appealOverturnPercentage()).isEqualTo(21);
  }

  @Test
  void everyModulesThroughputIsAskedForTheSameWindow() {
    RecordingModeration moderation = new RecordingModeration();
    AdminMetricsService service =
        new AdminMetricsService(
            moderation,
            reviews(0, new ReviewThroughput(0, 0)),
            verification(0, new VerificationThroughput(0, 0)),
            FIXED);

    service.current(FROM, NOW);

    assertThat(moderation.askedFrom).isEqualTo(FROM);
    assertThat(moderation.askedUntil).isEqualTo(NOW);
  }

  @Test
  void aWindowThatEndsBeforeItStartsIsRefused() {
    // The same rule as the audit timeline, for the same reason: an empty answer to an impossible
    // question reads as "nothing happened".
    assertThatThrownBy(() -> service().current(NOW, FROM))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AdminMetricsService service() {
    return new AdminMetricsService(
        moderation(
            12, Optional.of(NOW.minus(Duration.ofDays(3))), new ModerationThroughput(87, 14, 3)),
        reviews(31, new ReviewThroughput(64, 9)),
        verification(4, new VerificationThroughput(22, 7)),
        FIXED);
  }

  private static ModerationMetrics moderation(
      long open, Optional<Instant> oldest, ModerationThroughput throughput) {
    return new ModerationMetrics() {
      @Override
      public long openCases() {
        return open;
      }

      @Override
      public Optional<Instant> oldestOpenCaseAt() {
        return oldest;
      }

      @Override
      public ModerationThroughput between(Instant from, Instant until) {
        return throughput;
      }
    };
  }

  private static ReviewMetrics reviews(long awaiting, ReviewThroughput throughput) {
    return new ReviewMetrics() {
      @Override
      public long awaitingModeration() {
        return awaiting;
      }

      @Override
      public ReviewThroughput between(Instant from, Instant until) {
        return throughput;
      }
    };
  }

  private static VerificationMetrics verification(long pending, VerificationThroughput throughput) {
    return new VerificationMetrics() {
      @Override
      public long pendingCases() {
        return pending;
      }

      @Override
      public VerificationThroughput between(Instant from, Instant until) {
        return throughput;
      }
    };
  }

  private static final class RecordingModeration implements ModerationMetrics {
    private Instant askedFrom;
    private Instant askedUntil;

    @Override
    public long openCases() {
      return 0;
    }

    @Override
    public Optional<Instant> oldestOpenCaseAt() {
      return Optional.empty();
    }

    @Override
    public ModerationThroughput between(Instant from, Instant until) {
      askedFrom = from;
      askedUntil = until;
      return new ModerationThroughput(0, 0, 0);
    }
  }

  @Test
  void anOmittedWindowIsTheLastThirtyDaysEndingNow() {
    RecordingModeration moderation = new RecordingModeration();
    AdminMetricsService service =
        new AdminMetricsService(
            moderation,
            reviews(0, new ReviewThroughput(0, 0)),
            verification(0, new VerificationThroughput(0, 0)),
            FIXED);

    service.current(null, null);

    assertThat(moderation.askedUntil).isEqualTo(NOW);
    assertThat(moderation.askedFrom).isEqualTo(NOW.minus(Duration.ofDays(30)));
  }

  @Test
  void anOmittedStartIsThirtyDaysBeforeTheEndThatWasAskedFor() {
    // Not thirty days before *now*: a caller asking about last March means last March, and a
    // window that silently ended today would answer a question they did not ask.
    RecordingModeration moderation = new RecordingModeration();
    Instant lastMarch = Instant.parse("2026-03-31T00:00:00Z");
    new AdminMetricsService(
            moderation,
            reviews(0, new ReviewThroughput(0, 0)),
            verification(0, new VerificationThroughput(0, 0)),
            FIXED)
        .current(null, lastMarch);

    assertThat(moderation.askedUntil).isEqualTo(lastMarch);
    assertThat(moderation.askedFrom).isEqualTo(lastMarch.minus(Duration.ofDays(30)));
  }
}

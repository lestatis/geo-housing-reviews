package com.example.geohousing.app.metrics;

import com.example.geohousing.moderation.api.ModerationMetrics;
import com.example.geohousing.moderation.api.ModerationThroughput;
import com.example.geohousing.reviews.api.ReviewMetrics;
import com.example.geohousing.reviews.api.ReviewThroughput;
import com.example.geohousing.verification.api.VerificationMetrics;
import com.example.geohousing.verification.api.VerificationThroughput;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One answer out of three modules' counts.
 *
 * <p>The app composes rather than a module, for the reason the audit merge lives here too: each
 * module can answer only for its own tables, and the app is the one place entitled to hold all of
 * them. Unlike the audit trail these are not five answers to one question — each module answers a
 * different one — so they arrive through three named interfaces rather than one collected type.
 */
public class AdminMetricsService {

  private final ModerationMetrics moderation;
  private final ReviewMetrics reviews;
  private final VerificationMetrics verification;
  private final Clock clock;

  public AdminMetricsService(
      ModerationMetrics moderation,
      ReviewMetrics reviews,
      VerificationMetrics verification,
      Clock clock) {
    this.moderation = Objects.requireNonNull(moderation, "moderation");
    this.reviews = Objects.requireNonNull(reviews, "reviews");
    this.verification = Objects.requireNonNull(verification, "verification");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Long enough for an overturn rate to mean something, short enough to still describe today. */
  public static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

  /**
   * Queue depth as it stands, and throughput over {@code from} (inclusive) to {@code until}
   * (exclusive). Either bound may be null, meaning the default window ending now.
   *
   * @throws IllegalArgumentException if the window ends before it starts — answered rather than
   *     refused, an impossible window returns zeroes, and zeroes read as "nothing happened"
   */
  public AdminMetrics current(Instant from, Instant until) {
    Instant end = until == null ? clock.instant() : until;
    Instant start = from == null ? end.minus(DEFAULT_WINDOW) : from;
    return over(start, end);
  }

  private AdminMetrics over(Instant from, Instant until) {
    if (until.isBefore(from)) {
      throw new IllegalArgumentException("the window ends before it starts");
    }

    ModerationThroughput moderated = moderation.between(from, until);
    ReviewThroughput reviewed = reviews.between(from, until);
    VerificationThroughput verified = verification.between(from, until);

    return new AdminMetrics(
        moderation.openCases(),
        reviews.awaitingModeration(),
        verification.pendingCases(),
        oldestOpenCaseAgeDays(),
        moderated.decisions(),
        moderated.appealsHeard(),
        moderated.appealsOverturned(),
        overturnPercentage(moderated),
        verified.approved(),
        verified.rejected(),
        reviewed.published(),
        reviewed.removed());
  }

  /**
   * Measured against now rather than against the window's end: "how long has somebody been waiting"
   * is a question about today even when the window asked about is a past month.
   */
  private Long oldestOpenCaseAgeDays() {
    return moderation
        .oldestOpenCaseAt()
        .map(opened -> Duration.between(opened, clock.instant()).toDays())
        .orElse(null);
  }

  private static Long overturnPercentage(ModerationThroughput moderated) {
    if (moderated.appealsHeard() == 0) {
      return null;
    }
    return Math.round(100.0 * moderated.appealsOverturned() / moderated.appealsHeard());
  }
}

package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.moderation.api.ModerationMetrics;
import com.example.geohousing.moderation.api.ModerationThroughput;
import com.example.geohousing.moderation.domain.AppealStatus;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderation's counts, each a {@code COUNT} in the database rather than a list this side of it.
 *
 * <p>The distinction matters at the only volume that counts: a backlog worth measuring is a backlog
 * too big to load.
 */
@Repository
class JpaModerationMetrics implements ModerationMetrics {

  /**
   * Everything but {@code CLOSED}. Written as "which statuses are open" rather than "not closed" so
   * that a status added later has to be classified deliberately instead of silently counting as
   * open.
   */
  private static final List<String> OPEN =
      List.of(
          ModerationCaseStatus.OPEN.name(),
          ModerationCaseStatus.IN_REVIEW.name(),
          ModerationCaseStatus.DECIDED.name(),
          ModerationCaseStatus.APPEALED.name());

  private final SpringDataModerationCaseRepository cases;
  private final SpringDataModerationDecisionRepository decisions;
  private final SpringDataAppealRepository appeals;

  JpaModerationMetrics(
      SpringDataModerationCaseRepository cases,
      SpringDataModerationDecisionRepository decisions,
      SpringDataAppealRepository appeals) {
    this.cases = cases;
    this.decisions = decisions;
    this.appeals = appeals;
  }

  @Override
  @Transactional(readOnly = true)
  public long openCases() {
    return cases.countByStatusIn(OPEN);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Instant> oldestOpenCaseAt() {
    return Optional.ofNullable(cases.earliestOpenedAtAmong(OPEN));
  }

  @Override
  @Transactional(readOnly = true)
  public ModerationThroughput between(Instant from, Instant until) {
    return new ModerationThroughput(
        decisions.countDecidedBetween(from, until),
        appeals.countDecidedBetween(from, until),
        appeals.countWithStatusDecidedBetween(AppealStatus.OVERTURNED.name(), from, until));
  }
}

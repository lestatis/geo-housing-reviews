package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.moderation.application.ModerationCaseAlreadyOpenException;
import com.example.geohousing.moderation.application.ModerationCaseRepository;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.StaleModerationWriteException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the case port. */
@Repository
public class JpaModerationCaseRepository implements ModerationCaseRepository {

  private static final String ONE_LIVE_CASE_INDEX = "moderation_case_one_live_per_target_idx";

  private final SpringDataModerationCaseRepository cases;

  public JpaModerationCaseRepository(SpringDataModerationCaseRepository cases) {
    this.cases = cases;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ModerationCase> findById(ModerationCaseId caseId) {
    return cases.findById(caseId.value()).map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ModerationCase> findLiveByTarget(ModerationTargetRef target) {
    return cases
        .findByTargetTypeAndTargetIdAndStatusNot(
            target.type().name(), target.id(), ModerationCaseStatus.CLOSED.name())
        .map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ModerationCase> findQueue() {
    return cases.findByStatusNotOrderByOpenedAtAsc(ModerationCaseStatus.CLOSED.name()).stream()
        .map(ModerationJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void create(ModerationCase moderationCase) {
    try {
      // Flushed here so the partial-unique-index race surfaces while it can still be translated
      // into something the application layer knows how to handle.
      cases.saveAndFlush(ModerationJpaMapper.toEntity(moderationCase));
    } catch (DataIntegrityViolationException exception) {
      if (isOneLiveCaseViolation(exception)) {
        throw new ModerationCaseAlreadyOpenException(moderationCase.target());
      }
      throw exception;
    }
  }

  @Override
  @Transactional
  public void save(ModerationCase moderationCase) {
    ModerationCaseJpaEntity stored =
        cases
            .findById(moderationCase.id().value())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "cannot save a case that was never created: "
                            + moderationCase.id().value()));
    // The caller's version against the stored one. Hibernate's @Version alone does not cover this:
    // the row is re-read here, so its check is against what this transaction just loaded, not
    // against what the moderator actually read before deciding. A decision taken on version 3 would
    // be applied to version 4 without complaint, which is how a case ends up with two decisions.

    if (stored.version() != moderationCase.version()) {
      throw new StaleModerationWriteException(
          "this case changed since it was read; decide it again on what it says now");
    }

    // Mutated in place rather than replaced, so the write and the check above stay one unit.
    stored.apply(
        moderationCase.status().name(),
        moderationCase.riskLevel().name(),
        moderationCase.assignedModerator().map(m -> m.value()).orElse(null),
        moderationCase.firstResponseAt().orElse(null),
        moderationCase.closedAt().orElse(null),
        moderationCase.updatedAt());
    cases.saveAndFlush(stored);
  }

  private static boolean isOneLiveCaseViolation(DataIntegrityViolationException exception) {
    Throwable cause = exception.getCause();
    while (cause != null) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
          && ONE_LIVE_CASE_INDEX.equals(violation.getConstraintName())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}

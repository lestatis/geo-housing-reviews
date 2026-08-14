package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.moderation.application.AppealAlreadyFiledException;
import com.example.geohousing.moderation.application.AppealRepository;
import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.AppealStatus;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import com.example.geohousing.moderation.domain.StaleModerationWriteException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the appeal port. */
@Repository
public class JpaAppealRepository implements AppealRepository {

  private final SpringDataAppealRepository appeals;

  public JpaAppealRepository(SpringDataAppealRepository appeals) {
    this.appeals = appeals;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Appeal> findById(AppealId appealId) {
    return appeals.findById(appealId.value()).map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Appeal> findByDecision(ModerationDecisionId decisionId) {
    return appeals.findByDecisionId(decisionId.value()).map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Appeal> findPending() {
    return appeals.findByStatusOrderByCreatedAtAsc(AppealStatus.PENDING.name()).stream()
        .map(ModerationJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void create(Appeal appeal) {
    try {
      appeals.saveAndFlush(ModerationJpaMapper.toEntity(appeal));
    } catch (DataIntegrityViolationException exception) {
      // The service checks first, but two appeals can still race. The unique constraint on
      // decision_id is the authority, and the caller gets the same answer either way.
      throw new AppealAlreadyFiledException("this decision has already been appealed");
    }
  }

  @Override
  @Transactional
  public void save(Appeal appeal) {
    AppealJpaEntity stored =
        appeals
            .findById(appeal.id().value())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "cannot save an appeal that was never created: " + appeal.id().value()));
    // The same staleness the case repository has: re-reading the row means Hibernate checks the
    // version this transaction loaded, not the one the moderator saw. Two moderators can both open
    // a pending appeal, and the second verdict would overwrite the first — including the ordering
    // where one restores the content and the other's stale UPHOLD becomes the recorded outcome.
    if (stored.version() != appeal.version()) {
      throw new StaleModerationWriteException(
          "this appeal was decided while it was being read; it may only be heard once");
    }

    stored.apply(
        appeal.status().name(),
        appeal.outcomeExplanation().orElse(null),
        appeal.decidedBy().map(m -> m.value()).orElse(null),
        appeal.decidedAt().orElse(null));
    appeals.saveAndFlush(stored);
  }
}

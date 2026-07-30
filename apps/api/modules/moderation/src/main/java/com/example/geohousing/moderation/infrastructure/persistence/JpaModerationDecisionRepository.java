package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.moderation.application.ModerationDecisionRepository;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of the decision port. Append-only: the port offers no update, the entity has
 * no mutator, and the table has no {@code updated_at}.
 */
@Repository
public class JpaModerationDecisionRepository implements ModerationDecisionRepository {

  private final SpringDataModerationDecisionRepository decisions;

  public JpaModerationDecisionRepository(SpringDataModerationDecisionRepository decisions) {
    this.decisions = decisions;
  }

  @Override
  @Transactional
  public void append(ModerationDecision decision) {
    decisions.saveAndFlush(ModerationJpaMapper.toEntity(decision));
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Optional<ModerationDecision> findById(
      com.example.geohousing.moderation.domain.ModerationDecisionId decisionId) {
    return decisions.findById(decisionId.value()).map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ModerationDecision> findByCase(ModerationCaseId caseId) {
    return decisions.findByCaseIdOrderByDecidedAtAscIdAsc(caseId.value()).stream()
        .map(ModerationJpaMapper::toDomain)
        .toList();
  }
}

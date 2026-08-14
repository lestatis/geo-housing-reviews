package com.example.geohousing.moderation.infrastructure;

import com.example.geohousing.moderation.api.ModerationCaseUseCase;
import com.example.geohousing.moderation.application.ModerationCaseService;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction boundary for case work.
 *
 * <p>The stale-version check is only worth having if what it refuses is also undone. Without this,
 * two moderators holding the same case could both append a decision: the loser was told 409 while
 * its row stayed committed, so the case history showed two decisions and one of them belonged to
 * nobody's judgement of the current content.
 */
public class TransactionalModerationCaseService implements ModerationCaseUseCase {

  private final ModerationCaseService delegate;

  public TransactionalModerationCaseService(ModerationCaseService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Transactional
  public ModerationCase assign(ModerationCaseId caseId, ModeratorId moderatorId) {
    return delegate.assign(caseId, moderatorId);
  }

  @Override
  @Transactional
  public void claim(ModerationCaseId caseId, ModeratorId moderatorId) {
    delegate.claim(caseId, moderatorId);
  }

  @Override
  @Transactional
  public ModerationDecision decide(
      ModerationCaseId caseId,
      ModeratorId moderatorId,
      DecisionAction action,
      ReasonCode reasonCode,
      String publicExplanation,
      String internalNote) {
    return delegate.decide(
        caseId, moderatorId, action, reasonCode, publicExplanation, internalNote);
  }
}

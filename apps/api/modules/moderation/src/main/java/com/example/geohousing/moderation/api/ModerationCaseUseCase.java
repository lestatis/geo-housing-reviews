package com.example.geohousing.moderation.api;

import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;

/**
 * Working a moderation case.
 *
 * <p>Published so the transaction can sit around the whole use case. A decision appends a row, acts
 * on the content, and advances the case; without one boundary those land separately, and a stale
 * decision refused at the last step has already left its row behind.
 */
public interface ModerationCaseUseCase {

  ModerationCase assign(ModerationCaseId caseId, ModeratorId moderatorId);

  /** Takes the case for this moderator, tolerating one they already hold. */
  void claim(ModerationCaseId caseId, ModeratorId moderatorId);

  ModerationDecision decide(
      ModerationCaseId caseId,
      ModeratorId moderatorId,
      DecisionAction action,
      ReasonCode reasonCode,
      String publicExplanation,
      String internalNote);
}

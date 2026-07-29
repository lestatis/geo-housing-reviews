package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import java.util.List;

/**
 * Persistence port for decisions. Append-only: there is no {@code save}, because an appeal must be
 * able to show what was decided rather than what a decision later became.
 */
public interface ModerationDecisionRepository {

  void append(ModerationDecision decision);

  List<ModerationDecision> findByCase(ModerationCaseId caseId);
}

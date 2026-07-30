package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import java.util.ArrayList;
import java.util.List;

/** In-memory decision port for application tests. Append-only, like the real one. */
final class InMemoryModerationDecisionRepository implements ModerationDecisionRepository {

  final List<ModerationDecision> appended = new ArrayList<>();

  @Override
  public void append(ModerationDecision decision) {
    appended.add(decision);
  }

  @Override
  public java.util.Optional<ModerationDecision> findById(
      com.example.geohousing.moderation.domain.ModerationDecisionId decisionId) {
    return appended.stream().filter(d -> d.id().equals(decisionId)).findFirst();
  }

  @Override
  public List<ModerationDecision> findByCase(ModerationCaseId caseId) {
    return appended.stream().filter(decision -> decision.caseId().equals(caseId)).toList();
  }
}

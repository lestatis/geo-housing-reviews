package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory case port for application tests. */
final class InMemoryModerationCaseRepository implements ModerationCaseRepository {

  final Map<ModerationCaseId, ModerationCase> byId = new LinkedHashMap<>();

  @Override
  public Optional<ModerationCase> findById(ModerationCaseId caseId) {
    return Optional.ofNullable(byId.get(caseId));
  }

  @Override
  public Optional<ModerationCase> findLiveByTarget(ModerationTargetRef target) {
    return byId.values().stream()
        .filter(moderationCase -> moderationCase.target().equals(target))
        .filter(ModerationCase::isLive)
        .findFirst();
  }

  @Override
  public List<ModerationCase> findQueue() {
    return byId.values().stream()
        .filter(ModerationCase::isLive)
        .sorted(java.util.Comparator.comparing(ModerationCase::openedAt))
        .toList();
  }

  @Override
  public void create(ModerationCase moderationCase) {
    byId.put(moderationCase.id(), moderationCase);
  }

  @Override
  public void save(ModerationCase moderationCase) {
    byId.put(moderationCase.id(), moderationCase);
  }
}

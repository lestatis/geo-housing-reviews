package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.AppealStatus;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory appeal port for application tests. */
final class InMemoryAppealRepository implements AppealRepository {

  final Map<AppealId, Appeal> byId = new LinkedHashMap<>();

  @Override
  public Optional<Appeal> findById(AppealId appealId) {
    return Optional.ofNullable(byId.get(appealId));
  }

  @Override
  public Optional<Appeal> findByDecision(ModerationDecisionId decisionId) {
    return byId.values().stream()
        .filter(appeal -> appeal.decisionId().equals(decisionId))
        .findFirst();
  }

  @Override
  public List<Appeal> findPending() {
    return byId.values().stream()
        .filter(appeal -> appeal.status() == AppealStatus.PENDING)
        .sorted(Comparator.comparing(Appeal::createdAt))
        .toList();
  }

  @Override
  public void create(Appeal appeal) {
    if (findByDecision(appeal.decisionId()).isPresent()) {
      // Mirrors the real adapter, where the unique constraint on decision_id is the authority.
      throw new AppealAlreadyFiledException("this decision has already been appealed");
    }
    byId.put(appeal.id(), appeal);
  }

  @Override
  public void save(Appeal appeal) {
    byId.put(appeal.id(), appeal);
  }
}

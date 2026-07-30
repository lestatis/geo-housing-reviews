package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import java.util.List;
import java.util.Optional;

/** Persistence port for appeals. */
public interface AppealRepository {

  Optional<Appeal> findById(AppealId appealId);

  /** Backs the one-appeal-per-decision rule the schema also enforces with a unique constraint. */
  Optional<Appeal> findByDecision(ModerationDecisionId decisionId);

  /** Appeals still waiting to be heard, oldest first. */
  List<Appeal> findPending();

  void create(Appeal appeal);

  void save(Appeal appeal);
}

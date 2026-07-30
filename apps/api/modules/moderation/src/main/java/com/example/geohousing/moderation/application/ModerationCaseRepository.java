package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import java.util.List;
import java.util.Optional;

/** Persistence port for moderation cases. */
public interface ModerationCaseRepository {

  Optional<ModerationCase> findById(ModerationCaseId caseId);

  /**
   * The live case for a target, if one is open. This is what makes many reports converge on one
   * case instead of flooding the queue with duplicates.
   */
  Optional<ModerationCase> findLiveByTarget(ModerationTargetRef target);

  /**
   * Cases still needing a moderator's attention, oldest first. Ordering by when the case opened is
   * ordering by how long the first reporter has been waiting, which is the only fair queue.
   */
  List<ModerationCase> findQueue();

  void create(ModerationCase moderationCase);

  void save(ModerationCase moderationCase);
}

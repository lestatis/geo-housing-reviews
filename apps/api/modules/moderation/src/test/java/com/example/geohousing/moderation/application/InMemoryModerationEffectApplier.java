package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import java.util.ArrayList;
import java.util.List;

/** Records what would have been applied, and can be told to refuse as a stale-version conflict. */
final class InMemoryModerationEffectApplier implements ModerationEffectApplier {

  record Applied(ModerationTargetRef target, DecisionAction action, long expectedVersion) {}

  final List<Applied> applied = new ArrayList<>();
  boolean refuseAsConflict;

  @Override
  public void apply(
      ModerationTargetRef target,
      DecisionAction action,
      long expectedVersion,
      ModeratorId decidedBy,
      ReasonCode reasonCode) {
    if (refuseAsConflict) {
      throw new ModerationEffectConflictException("the content changed under the moderator");
    }
    applied.add(new Applied(target, action, expectedVersion));
  }
}

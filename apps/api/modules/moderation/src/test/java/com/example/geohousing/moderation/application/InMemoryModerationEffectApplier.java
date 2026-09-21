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
  final List<Applied> reversed = new ArrayList<>();
  boolean refuseAsConflict;
  boolean refuseReverse;

  /** What a restricting decision is told it created; null when it created nothing. */
  java.util.UUID restrictionToReport;

  /** The restrictions a reversal was asked to lift, which is the link this all exists to carry. */
  final List<java.util.UUID> liftedRestrictions = new ArrayList<>();

  @Override
  public java.util.Optional<java.util.UUID> apply(
      ModerationTargetRef target,
      DecisionAction action,
      long expectedVersion,
      ModeratorId decidedBy,
      ReasonCode reasonCode) {
    if (refuseAsConflict) {
      throw new ModerationEffectConflictException("the content changed under the moderator");
    }
    applied.add(new Applied(target, action, expectedVersion));
    return java.util.Optional.ofNullable(restrictionToReport);
  }

  @Override
  public void reverse(
      ModerationTargetRef target,
      DecisionAction action,
      long expectedVersion,
      ModeratorId decidedBy,
      ReasonCode reasonCode,
      java.util.UUID createdRestrictionId) {
    if (refuseReverse) {
      throw new ModerationEffectConflictException("the content could not be put back");
    }
    reversed.add(new Applied(target, action, expectedVersion));
    if (createdRestrictionId != null) {
      liftedRestrictions.add(createdRestrictionId);
    }
  }
}

package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;

/**
 * Outbound port for making a decision take effect on content another module owns.
 *
 * <p>Separate from {@link ModerationTargetLookup} on purpose: reading about content and changing it
 * are different authorities, and a target type may well support one without the other.
 */
public interface ModerationEffectApplier {

  /**
   * Applies the decision's effect, if the action has one. Actions with no content effect are a
   * no-op rather than an error — a decision to restrict an account or escalate for legal review is
   * a real outcome, it just is not a change to the content.
   *
   * @param expectedVersion the version the moderator judged, so the owning module can refuse if the
   *     content changed underneath them
   * @throws ModerationEffectConflictException if the content changed since {@code expectedVersion}
   */
  void apply(
      ModerationTargetRef target,
      DecisionAction action,
      long expectedVersion,
      ModeratorId decidedBy,
      ReasonCode reasonCode);
}

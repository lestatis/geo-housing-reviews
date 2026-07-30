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

  /**
   * Undoes a decision's effect on the content after an appeal overturned it.
   *
   * <p>Which decisions can be undone, and how, is the target module's business — a withheld review
   * is restored, a removed one reinstated, and an action that never touched the content is a no-op.
   *
   * @throws ModerationEffectConflictException if the content could not be put back
   */
  void reverse(
      ModerationTargetRef target,
      DecisionAction action,
      long expectedVersion,
      ModeratorId decidedBy,
      ReasonCode reasonCode);
}

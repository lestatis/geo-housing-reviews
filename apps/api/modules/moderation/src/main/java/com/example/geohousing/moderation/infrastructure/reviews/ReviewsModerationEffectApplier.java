package com.example.geohousing.moderation.infrastructure.reviews;

import com.example.geohousing.moderation.application.ModerationEffectApplier;
import com.example.geohousing.moderation.application.ModerationEffectConflictException;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.reviews.api.ReviewModerationConflictException;
import com.example.geohousing.reviews.api.ReviewModerationEffect;
import com.example.geohousing.reviews.api.ReviewModerationGateway;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Makes a moderation decision take effect on a review, through the reviews module's published
 * gateway.
 *
 * <p>The mapping from decision to content effect lives here rather than in the domain because
 * whether an action even <em>has</em> a content effect is a property of the target's module, not of
 * moderation policy. A decision this module can record is not automatically a change reviews knows
 * how to make.
 */
@Component
public class ReviewsModerationEffectApplier implements ModerationEffectApplier {

  private final ReviewModerationGateway gateway;

  public ReviewsModerationEffectApplier(ReviewModerationGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
  }

  @Override
  public void apply(
      ModerationTargetRef target,
      DecisionAction action,
      long expectedVersion,
      ModeratorId decidedBy,
      ReasonCode reasonCode) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(decidedBy, "decidedBy");
    Objects.requireNonNull(reasonCode, "reasonCode");
    if (target.type() != ModerationTargetType.REVIEW) {
      throw new IllegalArgumentException("no effect is wired for target type " + target.type());
    }

    Optional<ReviewModerationEffect> effect = contentEffectOf(action);
    if (effect.isEmpty()) {
      return;
    }
    try {
      gateway.apply(
          target.id(), effect.get(), expectedVersion, decidedBy.value(), reasonCode.value());
    } catch (ReviewModerationConflictException conflict) {
      // Translated into this module's vocabulary: the application layer should not have to know
      // which module refused, only that the content moved under the moderator.
      throw new ModerationEffectConflictException(conflict.getMessage());
    }
  }

  /**
   * Which decisions change what a reader sees.
   *
   * <p>The four with no effect are deliberate, not omissions. {@code APPROVE_WITH_REDACTION} and
   * {@code REQUEST_CHANGES} need a content-editing path that does not exist yet; {@code
   * RESTRICT_ACCOUNT} is identity's to apply, not reviews'; and {@code ESCALATE} is a handoff that
   * has decided nothing. Each is still a recorded decision with its reason and explanation.
   */
  private static Optional<ReviewModerationEffect> contentEffectOf(DecisionAction action) {
    return switch (action) {
      case APPROVE -> Optional.of(ReviewModerationEffect.PUBLISH);
      case REJECT -> Optional.of(ReviewModerationEffect.REJECT);
      case HIDE -> Optional.of(ReviewModerationEffect.HIDE);
      case REMOVE -> Optional.of(ReviewModerationEffect.REMOVE);
      case APPROVE_WITH_REDACTION, REQUEST_CHANGES, RESTRICT_ACCOUNT, ESCALATE -> Optional.empty();
    };
  }
}

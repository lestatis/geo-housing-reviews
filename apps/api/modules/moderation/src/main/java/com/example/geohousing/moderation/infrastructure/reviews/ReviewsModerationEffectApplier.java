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

    Optional<ReviewModerationEffect> effect = contentEffectOf(action, target);
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

  @Override
  public void reverse(
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

    Optional<ReviewModerationEffect> undo = undoEffectOf(action);
    if (undo.isEmpty()) {
      return;
    }
    try {
      // The version the decision judged is long stale by now — the takedown itself bumped it — so
      // the reversal reads the review's current version rather than trusting the recorded one.
      long current =
          gateway.find(target.id()).map(review -> review.version()).orElse(expectedVersion);
      gateway.apply(target.id(), undo.get(), current, decidedBy.value(), reasonCode.value());
    } catch (ReviewModerationConflictException conflict) {
      throw new ModerationEffectConflictException(conflict.getMessage());
    } catch (IllegalStateException | IllegalArgumentException refused) {
      // The owning module would not take the content back — most likely the author has since
      // published a replacement, and the one-live-review rule will not hold two. The appeal is not
      // silently marked overturned on content that is still gone; the moderator is told.
      throw new ModerationEffectConflictException(
          "the content could not be put back: " + refused.getMessage());
    }
  }

  /**
   * How each adverse decision is undone.
   *
   * <p>A withheld review is restored and a rejected or removed one reinstated — the narrow
   * appeal-only door added for exactly this (DECISION_LOG {@code P-014}). The rest never touched
   * the content, so there is nothing to put back: an account restriction is identity's to lift, and
   * a request for changes left the review where it was.
   */
  private static Optional<ReviewModerationEffect> undoEffectOf(DecisionAction action) {
    return switch (action) {
      case HIDE -> Optional.of(ReviewModerationEffect.RESTORE);
      case REJECT, REMOVE -> Optional.of(ReviewModerationEffect.REINSTATE);
      case APPROVE, APPROVE_WITH_REDACTION, REQUEST_CHANGES, RESTRICT_ACCOUNT, ESCALATE ->
          Optional.empty();
    };
  }

  /**
   * Which decisions change what a reader sees.
   *
   * <p>{@code APPROVE} depends on where the content already is. Approving a review that is awaiting
   * moderation publishes it — that is the pre-moderation queue clearing. Approving one that is
   * already published means a report was heard and not upheld, and the right effect is none at all:
   * the review was never withdrawn, so there is nothing to restore. Mapping it to an unconditional
   * publish would throw on the more common of the two.
   *
   * <p>The four with no effect are deliberate, not omissions. {@code APPROVE_WITH_REDACTION} and
   * {@code REQUEST_CHANGES} need a content-editing path that does not exist yet; {@code
   * RESTRICT_ACCOUNT} is identity's to apply, not reviews'; and {@code ESCALATE} is a handoff that
   * has decided nothing. Each is still a recorded decision with its reason and explanation.
   */
  private Optional<ReviewModerationEffect> contentEffectOf(
      DecisionAction action, ModerationTargetRef target) {
    return switch (action) {
      case APPROVE ->
          alreadyVisible(target) ? Optional.empty() : Optional.of(ReviewModerationEffect.PUBLISH);
      case REJECT -> Optional.of(ReviewModerationEffect.REJECT);
      case HIDE -> Optional.of(ReviewModerationEffect.HIDE);
      case REMOVE -> Optional.of(ReviewModerationEffect.REMOVE);
      case APPROVE_WITH_REDACTION, REQUEST_CHANGES, RESTRICT_ACCOUNT, ESCALATE -> Optional.empty();
    };
  }

  private boolean alreadyVisible(ModerationTargetRef target) {
    return gateway.find(target.id()).map(review -> review.published()).orElse(false);
  }
}

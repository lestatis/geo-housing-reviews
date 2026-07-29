package com.example.geohousing.reviews.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The reviews module's <em>inbound</em> published contract for moderation: the moderation module
 * asks about a review and applies a decision's effect through this port, without ever reading or
 * writing reviews' tables.
 *
 * <p>The projection is one-way. Reviews does not call moderation — that would make the two modules
 * mutually dependent, which the no-cycles rule forbids — so this module has no idea a case workflow
 * exists. It only knows that somebody with a moderator's account id asked for a state change and
 * gave a reason for it.
 */
public interface ReviewModerationGateway {

  /** What a moderator may know about the review, or empty if there is no such review. */
  Optional<ModeratableReview> find(UUID reviewId);

  /**
   * Applies a state change, recording it in this module's own moderation audit trail.
   *
   * <p>{@code expectedVersion} is the version the caller last saw. The check is enforced here
   * rather than by the caller because a moderator must never act on content that changed underneath
   * them, and a guarantee re-implemented on the far side of a boundary is a guarantee that will
   * eventually be forgotten.
   *
   * @return {@code true} if applied; {@code false} if no such review exists — the attempt is still
   *     audited, so an action against missing content leaves a trace
   * @throws ReviewModerationConflictException if the review changed since {@code expectedVersion}
   * @throws IllegalArgumentException if the reason code is missing, or the review's current state
   *     does not allow this effect
   */
  boolean apply(
      UUID reviewId,
      ReviewModerationEffect effect,
      long expectedVersion,
      UUID moderatorAccountId,
      String reasonCode);
}

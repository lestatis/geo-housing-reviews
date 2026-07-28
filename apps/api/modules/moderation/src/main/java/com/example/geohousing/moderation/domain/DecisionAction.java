package com.example.geohousing.moderation.domain;

/**
 * What a moderator decided to do. Mirrors MODERATION.md's decision actions and the {@code action}
 * check constraint on {@code moderation.moderation_decision}.
 */
public enum DecisionAction {
  APPROVE,
  APPROVE_WITH_REDACTION,
  REQUEST_CHANGES,
  REJECT,
  HIDE,
  REMOVE,
  RESTRICT_ACCOUNT,
  ESCALATE;

  /**
   * Whether the affected user must be told why.
   *
   * <p>Anything that costs someone their content or their access owes them an explanation specific
   * enough to correct the issue (MODERATION.md). {@code APPROVE} takes nothing away, and {@code
   * ESCALATE} is an internal handoff that has not yet decided anything — neither has an outcome to
   * explain.
   */
  public boolean requiresPublicExplanation() {
    return this != APPROVE && this != ESCALATE;
  }

  /** Whether this action is a final outcome rather than a handoff to someone else. */
  public boolean isConclusive() {
    return this != ESCALATE;
  }
}

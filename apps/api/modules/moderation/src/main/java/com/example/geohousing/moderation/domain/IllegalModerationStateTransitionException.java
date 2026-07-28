package com.example.geohousing.moderation.domain;

/** A moderation workflow was asked for a transition its current state does not allow. */
public class IllegalModerationStateTransitionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IllegalModerationStateTransitionException(String message) {
    super(message);
  }
}

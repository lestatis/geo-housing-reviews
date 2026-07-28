package com.example.geohousing.moderation.domain;

/**
 * Someone tried to decide an appeal against their own decision.
 *
 * <p>Its own type rather than a generic argument error because it is a due-process rule, not a
 * validation detail: MODERATION.md asks for a different reviewer, and a caller that hits this needs
 * to route the appeal elsewhere rather than fix an input.
 */
public class AppealDeciderConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AppealDeciderConflictException(String message) {
    super(message);
  }
}

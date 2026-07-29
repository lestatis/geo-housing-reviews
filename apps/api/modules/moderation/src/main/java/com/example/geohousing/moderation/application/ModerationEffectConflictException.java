package com.example.geohousing.moderation.application;

/**
 * The content changed since the moderator judged it, so the decision was not applied.
 *
 * <p>The moderator has to look again: the thing they read is not the thing that is published now,
 * and an explanation written about the old text may no longer be true of the new one.
 */
public class ModerationEffectConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ModerationEffectConflictException(String message) {
    super(message);
  }
}

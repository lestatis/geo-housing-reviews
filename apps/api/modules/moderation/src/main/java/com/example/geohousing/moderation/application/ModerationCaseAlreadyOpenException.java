package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationTargetRef;

/**
 * Another request opened the live case for this target first.
 *
 * <p>Not an error the caller needs to see: it means convergence worked. Intake catches this and
 * attaches the report to the case that won, which is the outcome the reporter wanted either way.
 */
public class ModerationCaseAlreadyOpenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ModerationCaseAlreadyOpenException(ModerationTargetRef target) {
    super("a live moderation case already exists for " + target.type() + " " + target.id());
  }
}

package com.example.geohousing.moderation.application;

/**
 * This decision has already been appealed.
 *
 * <p>One appeal per decision (MODERATION.md). A second bite is not an appeal, it is attrition — and
 * a channel that can be worked repeatedly is one an organised party uses to wear moderation down.
 */
public class AppealAlreadyFiledException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AppealAlreadyFiledException(String message) {
    super(message);
  }
}

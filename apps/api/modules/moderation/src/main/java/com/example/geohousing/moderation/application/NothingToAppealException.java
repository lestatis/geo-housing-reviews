package com.example.geohousing.moderation.application;

/**
 * There is no adverse decision on this content to appeal.
 *
 * <p>Covers both "nothing was decided" and "what was decided took nothing away" — an approval is
 * not something its beneficiary appeals.
 */
public class NothingToAppealException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NothingToAppealException(String message) {
    super(message);
  }
}

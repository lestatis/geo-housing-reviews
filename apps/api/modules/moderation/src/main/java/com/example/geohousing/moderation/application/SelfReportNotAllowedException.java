package com.example.geohousing.moderation.application;

/**
 * Someone tried to report their own content.
 *
 * <p>Refused because it is not a report — an author who wants their own content gone edits or
 * removes it. Allowing it would also let an author manufacture a case history against themselves.
 */
public class SelfReportNotAllowedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SelfReportNotAllowedException(String message) {
    super(message);
  }
}

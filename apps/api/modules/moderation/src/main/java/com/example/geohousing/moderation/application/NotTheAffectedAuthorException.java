package com.example.geohousing.moderation.application;

/**
 * Only the author a decision was made against may appeal it.
 *
 * <p>Forbidden rather than hidden: the caller can see that the content was acted on, so refusing
 * plainly discloses nothing they could not already tell.
 */
public class NotTheAffectedAuthorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NotTheAffectedAuthorException(String message) {
    super(message);
  }
}

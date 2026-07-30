package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.AppealId;

/** No appeal exists for this identifier, or it belongs to someone else. */
public class AppealNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AppealNotFoundException(AppealId appealId) {
    super("no appeal was found for identifier " + appealId.value());
  }
}

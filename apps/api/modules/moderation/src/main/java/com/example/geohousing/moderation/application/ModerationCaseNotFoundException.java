package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCaseId;

/** No case exists for this identifier. */
public class ModerationCaseNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ModerationCaseNotFoundException(ModerationCaseId caseId) {
    super("no moderation case for identifier " + caseId.value());
  }
}

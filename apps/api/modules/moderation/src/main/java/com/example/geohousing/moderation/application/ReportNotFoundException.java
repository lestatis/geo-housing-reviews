package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ReportId;

/** No report exists for this identifier, or it belongs to someone else. */
public class ReportNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ReportNotFoundException(ReportId reportId) {
    super("no report was found for identifier " + reportId.value());
  }
}

package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.ReportStatus;

/**
 * What a reporter is told about their report.
 *
 * <p>Deliberately coarser than {@link ReportStatus}: whether a report is merely received or already
 * attached to a case is queue plumbing, and telling a reporter the difference would let them infer
 * how busy moderation is and whether others have reported the same content.
 */
enum ReporterFacingStatus {
  AWAITING_MODERATION,
  RESOLVED,
  DISMISSED;

  static ReporterFacingStatus of(ReportStatus status) {
    return switch (status) {
      case OPEN, LINKED -> AWAITING_MODERATION;
      case RESOLVED -> RESOLVED;
      case DISMISSED -> DISMISSED;
    };
  }
}

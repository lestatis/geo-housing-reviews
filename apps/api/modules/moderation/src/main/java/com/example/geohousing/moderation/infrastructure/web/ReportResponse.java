package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.Report;
import java.time.Instant;

/**
 * What a reporter may see of their own report.
 *
 * <p>Carries no case id, no moderator, no decision and no other reporter. A reporter is owed the
 * progress of their own concern; the case around it belongs to moderation, and exposing its
 * identifier would let one reporter correlate their report with anyone else's.
 */
public record ReportResponse(
    String reportId, String category, ReporterFacingStatus status, Instant createdAt) {

  static ReportResponse from(Report report) {
    return new ReportResponse(
        report.id().value().toString(),
        report.category().name(),
        ReporterFacingStatus.of(report.status()),
        report.createdAt());
  }
}

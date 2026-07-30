package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReporterId;
import java.util.Objects;

/**
 * Lets a reporter follow their own report, and nobody else's.
 *
 * <p>Someone else's report is reported as missing rather than forbidden. A "forbidden" would
 * confirm that a report exists for that identifier, which is enough to learn that a particular
 * piece of content has been reported — and, by probing, roughly when and how often.
 */
public final class ReportQueryService {

  private final ReportRepository reportRepository;

  public ReportQueryService(ReportRepository reportRepository) {
    this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository");
  }

  /**
   * The reporter's own report.
   *
   * @throws ReportNotFoundException if there is no such report, or it belongs to someone else
   */
  public Report findOwn(ReportId reportId, ReporterId reporterId) {
    Objects.requireNonNull(reportId, "reportId");
    Objects.requireNonNull(reporterId, "reporterId");
    return reportRepository
        .findById(reportId)
        .filter(report -> report.reporterId().equals(reporterId))
        .orElseThrow(() -> new ReportNotFoundException(reportId));
  }
}

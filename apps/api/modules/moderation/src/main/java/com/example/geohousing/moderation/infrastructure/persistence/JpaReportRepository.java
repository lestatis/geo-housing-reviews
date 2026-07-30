package com.example.geohousing.moderation.infrastructure.persistence;

import com.example.geohousing.moderation.application.DuplicateReportException;
import com.example.geohousing.moderation.application.ReportRepository;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReportStatus;
import com.example.geohousing.moderation.domain.ReporterId;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the report port. */
@Repository
public class JpaReportRepository implements ReportRepository {

  private static final String ONE_LIVE_REPORT_INDEX = "report_one_live_per_reporter_target_idx";
  private static final List<String> LIVE_STATUSES =
      List.of(ReportStatus.OPEN.name(), ReportStatus.LINKED.name());

  private final SpringDataReportRepository reports;

  public JpaReportRepository(SpringDataReportRepository reports) {
    this.reports = reports;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Report> findLive(ReporterId reporterId, ModerationTargetRef target) {
    return reports
        .findByReporterAccountIdAndTargetTypeAndTargetIdAndStatusIn(
            reporterId.value(), target.type().name(), target.id(), LIVE_STATUSES)
        .map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Report> findById(ReportId reportId) {
    return reports.findById(reportId.value()).map(ModerationJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Report> findByCase(ModerationCaseId caseId) {
    return reports.findByCaseIdOrderByCreatedAtAsc(caseId.value()).stream()
        .map(ModerationJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void create(Report report) {
    try {
      reports.saveAndFlush(ModerationJpaMapper.toEntity(report));
    } catch (DataIntegrityViolationException exception) {
      if (isOneLiveReportViolation(exception)) {
        // The service pre-checks, but two requests from the same account can still race. The
        // database is the authority, and the caller gets the same answer either way.
        throw new DuplicateReportException(
            "this account already has a live report about this content");
      }
      throw exception;
    }
  }

  @Override
  @Transactional
  public void save(Report report) {
    ReportJpaEntity stored =
        reports
            .findById(report.id().value())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "cannot save a report that was never created: " + report.id().value()));
    stored.apply(report.status().name(), report.caseId().map(ModerationCaseId::value).orElse(null));
    reports.saveAndFlush(stored);
  }

  private static boolean isOneLiveReportViolation(DataIntegrityViolationException exception) {
    Throwable cause = exception.getCause();
    while (cause != null) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
          && ONE_LIVE_REPORT_INDEX.equals(violation.getConstraintName())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}

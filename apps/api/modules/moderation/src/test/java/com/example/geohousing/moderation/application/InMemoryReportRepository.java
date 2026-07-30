package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReporterId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory report port for application tests. */
final class InMemoryReportRepository implements ReportRepository {

  final Map<ReportId, Report> byId = new LinkedHashMap<>();

  @Override
  public Optional<Report> findLive(ReporterId reporterId, ModerationTargetRef target) {
    return byId.values().stream()
        .filter(report -> report.reporterId().equals(reporterId))
        .filter(report -> report.target().equals(target))
        .filter(Report::isLive)
        .findFirst();
  }

  @Override
  public Optional<Report> findById(ReportId reportId) {
    return Optional.ofNullable(byId.get(reportId));
  }

  @Override
  public List<Report> findByCase(ModerationCaseId caseId) {
    List<Report> found = new ArrayList<>();
    for (Report report : byId.values()) {
      if (report.caseId().filter(caseId::equals).isPresent()) {
        found.add(report);
      }
    }
    return found;
  }

  @Override
  public void create(Report report) {
    byId.put(report.id(), report);
  }

  @Override
  public void save(Report report) {
    byId.put(report.id(), report);
  }
}

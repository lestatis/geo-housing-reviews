package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import java.util.List;
import java.util.Objects;

/** Reads the moderator queue and the detail of one case. */
public final class ModerationQueueService {

  private final ModerationCaseRepository caseRepository;
  private final ReportRepository reportRepository;
  private final ModerationDecisionRepository decisionRepository;

  public ModerationQueueService(
      ModerationCaseRepository caseRepository,
      ReportRepository reportRepository,
      ModerationDecisionRepository decisionRepository) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository");
    this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
  }

  /** Cases still needing work, oldest first — the reporter who has waited longest comes first. */
  public List<ModerationCaseSummary> queue() {
    return caseRepository.findQueue().stream()
        .map(
            moderationCase ->
                ModerationCaseSummary.of(
                    moderationCase, reportRepository.findByCase(moderationCase.id())))
        .toList();
  }

  /**
   * One case in full.
   *
   * @throws ModerationCaseNotFoundException if there is no such case
   */
  public ModerationCaseDetail detail(ModerationCaseId caseId) {
    Objects.requireNonNull(caseId, "caseId");
    ModerationCase moderationCase =
        caseRepository
            .findById(caseId)
            .orElseThrow(() -> new ModerationCaseNotFoundException(caseId));
    return new ModerationCaseDetail(
        moderationCase, reportRepository.findByCase(caseId), decisionRepository.findByCase(caseId));
  }
}

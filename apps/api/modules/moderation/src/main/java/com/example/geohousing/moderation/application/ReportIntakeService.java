package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.CaseTrigger;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReporterId;
import com.example.geohousing.moderation.domain.RiskLevel;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Receives reports and converges them onto one live case per target.
 *
 * <p>A report is evidence, never a decision: nothing here changes what any reader sees. What it
 * does decide is which case the concern joins — and that convergence is a defence, not just
 * tidiness. One case per target means an organised group cannot bury the queue in duplicates about
 * the same content, which is the brigading MODERATION.md asks the platform to resist.
 */
public final class ReportIntakeService {

  private final ReportRepository reportRepository;
  private final ModerationCaseRepository caseRepository;
  private final ModerationTargetLookup targetLookup;
  private final Clock clock;

  public ReportIntakeService(
      ReportRepository reportRepository,
      ModerationCaseRepository caseRepository,
      ModerationTargetLookup targetLookup,
      Clock clock) {
    this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository");
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.targetLookup = Objects.requireNonNull(targetLookup, "targetLookup");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Files a report and attaches it to the live case for the target, opening one if none exists.
   *
   * @throws ModerationTargetNotFoundException if the content does not exist or is not moderatable
   * @throws SelfReportNotAllowedException if the reporter wrote the content
   * @throws DuplicateReportException if this account already has a live report about it
   */
  public Report file(
      ReporterId reporterId,
      ModerationTargetRef target,
      ReportCategory category,
      String description) {
    Objects.requireNonNull(reporterId, "reporterId");
    Objects.requireNonNull(target, "target");

    ModeratableTarget content =
        targetLookup.find(target).orElseThrow(() -> new ModerationTargetNotFoundException(target));
    if (content.authorAccountId().equals(reporterId.value())) {
      throw new SelfReportNotAllowedException("an author cannot report their own content");
    }
    if (reportRepository.findLive(reporterId, target).isPresent()) {
      throw new DuplicateReportException(
          "this account already has a live report about this content");
    }

    // Built before any case is opened, so a report the domain refuses (an "other" with nothing
    // written) leaves no case behind for a moderator to puzzle over.
    Report report =
        Report.file(
            ReportId.of(UUID.randomUUID()), target, reporterId, category, description, clock);

    ModerationCase moderationCase =
        caseRepository.findLiveByTarget(target).orElseGet(() -> openCaseFor(target));
    report.linkTo(moderationCase.id());
    reportRepository.create(report);
    return report;
  }

  private ModerationCase openCaseFor(ModerationTargetRef target) {
    ModerationCase opened =
        ModerationCase.open(
            ModerationCaseId.of(UUID.randomUUID()),
            target,
            CaseTrigger.REPORT,
            RiskLevel.STANDARD,
            clock);
    caseRepository.create(opened);
    return opened;
  }
}

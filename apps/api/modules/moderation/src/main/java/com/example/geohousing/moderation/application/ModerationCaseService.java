package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.Report;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Assigns cases to moderators and records what they decided.
 *
 * <p>Applying a decision's effect on the content is deliberately not here: that crosses a module
 * boundary and arrives in chunk 4 through the owning module's published contract. What this owns is
 * the record — who decided, why, under which policy, and against which version of the content.
 */
public final class ModerationCaseService {

  private final ModerationCaseRepository caseRepository;
  private final ModerationDecisionRepository decisionRepository;
  private final ReportRepository reportRepository;
  private final ModerationTargetLookup targetLookup;
  private final PolicyVersion policyVersion;
  private final Clock clock;

  public ModerationCaseService(
      ModerationCaseRepository caseRepository,
      ModerationDecisionRepository decisionRepository,
      ReportRepository reportRepository,
      ModerationTargetLookup targetLookup,
      PolicyVersion policyVersion,
      Clock clock) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
    this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository");
    this.targetLookup = Objects.requireNonNull(targetLookup, "targetLookup");
    this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * A moderator takes the case. Reassignment is allowed so a conflict of interest can be handed on.
   */
  public ModerationCase assign(ModerationCaseId caseId, ModeratorId moderatorId) {
    ModerationCase moderationCase = require(caseId);
    moderationCase.assignTo(moderatorId, clock);
    caseRepository.save(moderationCase);
    return moderationCase;
  }

  /**
   * Records a decision on a case that is being worked, and closes out the reports that fed it.
   *
   * @throws ModerationCaseNotFoundException if no such case exists
   * @throws com.example.geohousing.moderation.domain.IllegalModerationStateTransitionException if
   *     the case is not in review, so no decision exists without a moderator accountable for it
   */
  public ModerationDecision decide(
      ModerationCaseId caseId,
      ModeratorId moderatorId,
      DecisionAction action,
      ReasonCode reasonCode,
      String publicExplanation,
      String internalNote) {
    ModerationCase moderationCase = require(caseId);

    // The decision is built first so its own rules — an adverse action owes the user an
    // explanation — refuse before the case moves. A half-applied decision would leave a case
    // marked decided with nothing recorded to explain it.
    ModerationDecision decision =
        ModerationDecision.record(
            ModerationDecisionId.of(UUID.randomUUID()),
            caseId,
            action,
            reasonCode,
            policyVersion,
            publicExplanation,
            internalNote,
            targetLookup.find(moderationCase.target()).map(ModeratableTarget::version).orElse(null),
            moderatorId,
            clock);

    moderationCase.markDecided(clock);
    decisionRepository.append(decision);
    caseRepository.save(moderationCase);
    closeOutReports(caseId, action);
    return decision;
  }

  /**
   * A conclusive outcome closes the reports that fed the case: resolved when the concern was acted
   * on, dismissed when it was heard and not upheld. The difference is what a reporter is owed —
   * recording an unupheld concern as "resolved" would overstate what happened. An escalation has
   * decided nothing yet, so it leaves the reports open.
   */
  private void closeOutReports(ModerationCaseId caseId, DecisionAction action) {
    if (!action.isConclusive()) {
      return;
    }
    for (Report report : reportRepository.findByCase(caseId)) {
      if (!report.isLive()) {
        continue;
      }
      if (action == DecisionAction.APPROVE) {
        report.dismiss();
      } else {
        report.resolve();
      }
      reportRepository.save(report);
    }
  }

  private ModerationCase require(ModerationCaseId caseId) {
    Objects.requireNonNull(caseId, "caseId");
    return caseRepository
        .findById(caseId)
        .orElseThrow(() -> new ModerationCaseNotFoundException(caseId));
  }
}

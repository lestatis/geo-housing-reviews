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
 * Assigns cases to moderators, applies what they decided to the content, and records the decision.
 *
 * <p>The content belongs to another module and is only ever reached through {@link
 * ModerationEffectApplier}, which resolves to that module's published contract. What this module
 * owns is the record — who decided, why, under which policy version, and against which version of
 * the content.
 */
public final class ModerationCaseService {

  private final ModerationCaseRepository caseRepository;
  private final ModerationDecisionRepository decisionRepository;
  private final ReportRepository reportRepository;
  private final ModerationTargetLookup targetLookup;
  private final ModerationEffectApplier effectApplier;
  private final PolicyVersion policyVersion;
  private final Clock clock;

  public ModerationCaseService(
      ModerationCaseRepository caseRepository,
      ModerationDecisionRepository decisionRepository,
      ReportRepository reportRepository,
      ModerationTargetLookup targetLookup,
      ModerationEffectApplier effectApplier,
      PolicyVersion policyVersion,
      Clock clock) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
    this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository");
    this.targetLookup = Objects.requireNonNull(targetLookup, "targetLookup");
    this.effectApplier = Objects.requireNonNull(effectApplier, "effectApplier");
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
    Long judgedVersion =
        targetLookup.find(moderationCase.target()).map(ModeratableTarget::version).orElse(null);

    // Built before anything happens so its own rules — an adverse action owes the user an
    // explanation — refuse while the case is still untouched.
    ModerationDecision decision =
        ModerationDecision.record(
            ModerationDecisionId.of(UUID.randomUUID()),
            caseId,
            action,
            reasonCode,
            policyVersion,
            publicExplanation,
            internalNote,
            judgedVersion,
            moderatorId,
            clock);

    // The case must be in review before anything is applied, so a decision never takes effect
    // without a moderator accountable for it. Checked here rather than after the effect, because
    // the effect is the part that cannot be undone by throwing.
    moderationCase.requireDecidable();

    // Effect first, then record (founder decision, 2026-07-29). If recording fails after this, the
    // content is correctly withheld and the owning module's own audit row — written atomically with
    // its mutation — already carries the action and reason; the case stays IN_REVIEW and is
    // retryable. Recording first would risk an audit trail asserting a review was removed while it
    // is still publicly visible, and an appeal referencing a decision that never took effect.
    if (judgedVersion != null) {
      effectApplier.apply(moderationCase.target(), action, judgedVersion, moderatorId, reasonCode);
    }

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

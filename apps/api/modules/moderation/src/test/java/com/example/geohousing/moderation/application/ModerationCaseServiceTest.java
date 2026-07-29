package com.example.geohousing.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.IllegalModerationStateTransitionException;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportStatus;
import com.example.geohousing.moderation.domain.ReporterId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationCaseServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final PolicyVersion POLICY = PolicyVersion.of(2);

  private final InMemoryReportRepository reports = new InMemoryReportRepository();
  private final InMemoryModerationCaseRepository cases = new InMemoryModerationCaseRepository();
  private final InMemoryModerationDecisionRepository decisions =
      new InMemoryModerationDecisionRepository();
  private final InMemoryModerationTargetLookup targets = new InMemoryModerationTargetLookup();

  private final ReportIntakeService intake =
      new ReportIntakeService(reports, cases, targets, CLOCK);
  private final ModerationCaseService service =
      new ModerationCaseService(cases, decisions, reports, targets, POLICY, CLOCK);

  @Test
  void takingACasePutsItInReviewUnderANamedModerator() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();

    ModerationCase taken = service.assign(caseId, moderator);

    assertThat(taken.status()).isEqualTo(ModerationCaseStatus.IN_REVIEW);
    assertThat(taken.assignedModerator()).contains(moderator);
    assertThat(cases.findById(caseId).orElseThrow().assignedModerator()).contains(moderator);
  }

  @Test
  void assigningACaseThatDoesNotExistIsRefused() {
    assertThatThrownBy(() -> service.assign(ModerationCaseId.of(UUID.randomUUID()), moderator()))
        .isInstanceOf(ModerationCaseNotFoundException.class);
  }

  @Test
  void aDecisionRecordsWhoMadeItWhyAndUnderWhichPolicy() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);

    ModerationDecision decision =
        service.decide(
            caseId,
            moderator,
            DecisionAction.REMOVE,
            ReasonCode.of("DOXXING"),
            "Your review identified a neighbour.",
            "reporter unrelated to author");

    assertThat(decision.decidedBy()).isEqualTo(moderator);
    assertThat(decision.reasonCode()).isEqualTo(ReasonCode.of("DOXXING"));
    assertThat(decision.policyVersion()).isEqualTo(POLICY);
    assertThat(decision.decidedAt()).isEqualTo(NOW);
    assertThat(decisions.appended).containsExactly(decision);
  }

  @Test
  void aDecisionStampsTheVersionOfTheContentItJudged() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 7L);
    ModerationCaseId caseId = reportedCase(target);
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);

    ModerationDecision decision =
        service.decide(
            caseId,
            moderator,
            DecisionAction.HIDE,
            ReasonCode.of("PRIVACY_RISK"),
            "Redact it.",
            null);

    // Without this an edit after the decision is indistinguishable from what the moderator read.
    assertThat(decision.affectedTargetVersion()).contains(7L);
  }

  @Test
  void decidingMovesTheCaseOnAndClosesOutTheReportsThatFedIt() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    ModerationCaseId caseId = reportedCase(target);
    intake.file(reporter(), target, ReportCategory.HARASSMENT_OR_THREAT, null);
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);

    service.decide(
        caseId, moderator, DecisionAction.REMOVE, ReasonCode.of("DOXXING"), "Removed.", null);

    assertThat(cases.findById(caseId).orElseThrow().status())
        .isEqualTo(ModerationCaseStatus.DECIDED);
    assertThat(reports.findByCase(caseId))
        .isNotEmpty()
        .allSatisfy(report -> assertThat(report.status()).isEqualTo(ReportStatus.RESOLVED));
  }

  @Test
  void approvingTheContentDismissesTheReportsRatherThanResolvingThem() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);

    service.decide(caseId, moderator, DecisionAction.APPROVE, ReasonCode.of("CLEAN"), null, null);

    // The concern was heard and not upheld. Recording that as "resolved" would overstate it, and
    // the difference is what a reporter is owed.
    assertThat(reports.findByCase(caseId))
        .isNotEmpty()
        .allSatisfy(report -> assertThat(report.status()).isEqualTo(ReportStatus.DISMISSED));
  }

  @Test
  void anUnassignedCaseCannotProduceADecision() {
    ModerationCaseId caseId = reportedCase();

    // No anonymous outcomes: somebody is accountable for every decision.
    assertThatThrownBy(
            () ->
                service.decide(
                    caseId,
                    moderator(),
                    DecisionAction.REMOVE,
                    ReasonCode.of("DOXXING"),
                    "Removed.",
                    null))
        .isInstanceOf(IllegalModerationStateTransitionException.class);

    assertThat(decisions.appended).isEmpty();
  }

  @Test
  void anAdverseDecisionWithoutAnExplanationIsRefusedAndChangesNothing() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);

    assertThatThrownBy(
            () ->
                service.decide(
                    caseId, moderator, DecisionAction.REMOVE, ReasonCode.of("DOXXING"), " ", null))
        .isInstanceOf(IllegalArgumentException.class);

    // Refused before anything moved: the case is still waiting to be decided properly.
    assertThat(decisions.appended).isEmpty();
    assertThat(cases.findById(caseId).orElseThrow().status())
        .isEqualTo(ModerationCaseStatus.IN_REVIEW);
    assertThat(reports.findByCase(caseId))
        .allSatisfy(report -> assertThat(report.status()).isEqualTo(ReportStatus.LINKED));
  }

  @Test
  void decidingACaseThatDoesNotExistIsRefused() {
    assertThatThrownBy(
            () ->
                service.decide(
                    ModerationCaseId.of(UUID.randomUUID()),
                    moderator(),
                    DecisionAction.APPROVE,
                    ReasonCode.of("CLEAN"),
                    null,
                    null))
        .isInstanceOf(ModerationCaseNotFoundException.class);
  }

  @Test
  void aCaseIsDecidedOnceSoASecondDecisionIsRefused() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);
    service.decide(caseId, moderator, DecisionAction.APPROVE, ReasonCode.of("CLEAN"), null, null);

    assertThatThrownBy(
            () ->
                service.decide(
                    caseId,
                    moderator,
                    DecisionAction.REMOVE,
                    ReasonCode.of("DOXXING"),
                    "Changed my mind.",
                    null))
        .isInstanceOf(IllegalModerationStateTransitionException.class);

    assertThat(decisions.appended).hasSize(1);
  }

  @Test
  void escalatingIsAnInternalHandoffThatStillRecordsWhoAskedForIt() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();
    service.assign(caseId, moderator);

    ModerationDecision decision =
        service.decide(
            caseId,
            moderator,
            DecisionAction.ESCALATE,
            ReasonCode.of("LEGAL_REVIEW"),
            null,
            "referred to counsel");

    assertThat(decision.publicExplanation()).isEmpty();
    assertThat(decision.internalNote()).contains("referred to counsel");
    assertThat(decision.decidedBy()).isEqualTo(moderator);
  }

  private ModerationCaseId reportedCase() {
    return reportedCase(targets.givenReviewBy(UUID.randomUUID(), 1L));
  }

  private ModerationCaseId reportedCase(ModerationTargetRef target) {
    Report report = intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);
    return report.caseId().orElseThrow();
  }

  private static ReporterId reporter() {
    return ReporterId.of(UUID.randomUUID());
  }

  private static ModeratorId moderator() {
    return ModeratorId.of(UUID.randomUUID());
  }
}

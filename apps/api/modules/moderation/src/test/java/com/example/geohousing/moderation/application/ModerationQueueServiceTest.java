package com.example.geohousing.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.api.AccountStanding;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReporterId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationQueueServiceTest {

  /** Nobody is restricted unless a test says so. */
  private static final AccountStanding UNRESTRICTED = accountId -> false;

  private static final Instant T0 = Instant.parse("2026-07-29T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

  private final InMemoryReportRepository reports = new InMemoryReportRepository();
  private final InMemoryModerationCaseRepository cases = new InMemoryModerationCaseRepository();
  private final InMemoryModerationDecisionRepository decisions =
      new InMemoryModerationDecisionRepository();
  private final InMemoryModerationTargetLookup targets = new InMemoryModerationTargetLookup();
  private final InMemoryModerationEffectApplier effects = new InMemoryModerationEffectApplier();

  private final ReportIntakeService intake =
      new ReportIntakeService(reports, cases, targets, UNRESTRICTED, CLOCK);
  private final ModerationCaseService caseService =
      new ModerationCaseService(
          cases, decisions, reports, targets, effects, PolicyVersion.of(1), CLOCK);
  private final ModerationQueueService queue =
      new ModerationQueueService(cases, reports, decisions);

  @Test
  void theQueueCountsConcernsWithoutCarryingWhoRaisedThem() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);
    intake.file(reporter(), target, ReportCategory.HARASSMENT_OR_THREAT, null);

    ModerationCaseSummary summary = queue.queue().getFirst();

    // One account can raise at most one live report per target, so the count is the number of
    // distinct people concerned — the thing a moderator actually needs — without naming any.
    assertThat(summary.concernCount()).isEqualTo(2);
    assertThat(ModerationCaseSummary.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactlyInAnyOrder("moderationCase", "concernCount");
  }

  @Test
  void theOldestCaseComesFirstBecauseItsReporterHasWaitedLongest() {
    ModerationTargetRef older = targets.givenReviewBy(UUID.randomUUID(), 1L);
    ModerationTargetRef newer = targets.givenReviewBy(UUID.randomUUID(), 1L);
    intake.file(reporter(), older, ReportCategory.PERSONAL_DATA, null);
    new ReportIntakeService(reports, cases, targets, UNRESTRICTED, at(3600))
        .file(reporter(), newer, ReportCategory.PERSONAL_DATA, null);

    assertThat(queue.queue())
        .extracting(summary -> summary.moderationCase().target())
        .containsExactly(older, newer);
  }

  @Test
  void aSettledCaseLeavesTheQueue() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    Report report = intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);
    ModerationCaseId caseId = report.caseId().orElseThrow();
    ModerationCase moderationCase = cases.findById(caseId).orElseThrow();
    moderationCase.assignTo(moderator(), CLOCK);
    moderationCase.markDecided(CLOCK);
    moderationCase.close(CLOCK);
    cases.save(moderationCase);

    assertThat(queue.queue()).isEmpty();
  }

  @Test
  void aCaseInDetailCarriesTheConcernsAndEveryDecisionSoFar() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 4L);
    Report report = intake.file(reporter(), target, ReportCategory.OTHER, "Door code posted.");
    ModerationCaseId caseId = report.caseId().orElseThrow();
    ModeratorId moderator = moderator();
    caseService.assign(caseId, moderator);
    caseService.decide(
        caseId, moderator, DecisionAction.REMOVE, ReasonCode.of("DOXXING"), "Removed.", "note");

    ModerationCaseDetail detail = queue.detail(caseId);

    assertThat(detail.moderationCase().id()).isEqualTo(caseId);
    assertThat(detail.reports())
        .singleElement()
        .satisfies(r -> assertThat(r.description()).contains("Door code posted."));
    assertThat(detail.decisions())
        .singleElement()
        .satisfies(d -> assertThat(d.action()).isEqualTo(DecisionAction.REMOVE));
  }

  @Test
  void aCaseThatDoesNotExistIsRefused() {
    assertThatThrownBy(() -> queue.detail(ModerationCaseId.of(UUID.randomUUID())))
        .isInstanceOf(ModerationCaseNotFoundException.class);
  }

  @Test
  void anEmptyQueueIsEmptyRatherThanAnError() {
    assertThat(queue.queue()).isEmpty();
  }

  @Test
  void claimingTakesAnUnheldCase() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId moderator = moderator();

    caseService.claim(caseId, moderator);

    assertThat(cases.findById(caseId).orElseThrow().assignedModerator()).contains(moderator);
  }

  @Test
  void claimingDoesNotDisplaceTheModeratorAlreadyHoldingTheCase() {
    ModerationCaseId caseId = reportedCase();
    ModeratorId first = moderator();
    caseService.assign(caseId, first);

    caseService.claim(caseId, moderator());

    // The case record keeps saying who owns it; a decision still records who actually made it.
    assertThat(cases.findById(caseId).orElseThrow().assignedModerator()).contains(first);
  }

  @Test
  void claimingACaseThatDoesNotExistIsRefused() {
    assertThatThrownBy(() -> caseService.claim(ModerationCaseId.of(UUID.randomUUID()), moderator()))
        .isInstanceOf(ModerationCaseNotFoundException.class);
  }

  private ModerationCaseId reportedCase() {
    return intake
        .file(
            reporter(),
            targets.givenReviewBy(UUID.randomUUID(), 1L),
            ReportCategory.PERSONAL_DATA,
            null)
        .caseId()
        .orElseThrow();
  }

  private static Clock at(int secondsAfterStart) {
    return Clock.fixed(T0.plusSeconds(secondsAfterStart), ZoneOffset.UTC);
  }

  private static ReporterId reporter() {
    return ReporterId.of(UUID.randomUUID());
  }

  private static ModeratorId moderator() {
    return ModeratorId.of(UUID.randomUUID());
  }
}

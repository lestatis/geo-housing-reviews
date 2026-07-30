package com.example.geohousing.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.moderation.application.ModerationCaseAlreadyOpenException;
import com.example.geohousing.moderation.application.ModerationCaseRepository;
import com.example.geohousing.moderation.application.ModerationDecisionRepository;
import com.example.geohousing.moderation.application.ReportRepository;
import com.example.geohousing.moderation.domain.CaseTrigger;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationDecisionId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.PolicyVersion;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReportStatus;
import com.example.geohousing.moderation.domain.ReporterId;
import com.example.geohousing.moderation.domain.RiskLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the moderation adapters against a real PostgreSQL: the mappings round-trip, decisions are
 * append-only in practice and not just by intent, and both partial unique indexes translate into
 * the application's own vocabulary when two requests race.
 */
@Testcontainers
@SpringBootTest
class ModerationPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

  @Autowired private ModerationCaseRepository cases;
  @Autowired private ReportRepository reports;
  @Autowired private ModerationDecisionRepository decisions;

  @Test
  void aCaseSurvivesARoundTripWithEverythingThatMattersForTheQueue() {
    ModerationTargetRef target = review();
    ModerationCase opened = openCase(target, CaseTrigger.LEGAL_REQUEST, RiskLevel.LEGAL);
    ModeratorId moderator = ModeratorId.of(UUID.randomUUID());
    opened.assignTo(moderator, CLOCK);
    cases.save(opened);

    ModerationCase loaded = cases.findById(opened.id()).orElseThrow();

    assertThat(loaded.target()).isEqualTo(target);
    assertThat(loaded.trigger()).isEqualTo(CaseTrigger.LEGAL_REQUEST);
    assertThat(loaded.riskLevel()).isEqualTo(RiskLevel.LEGAL);
    assertThat(loaded.status()).isEqualTo(ModerationCaseStatus.IN_REVIEW);
    assertThat(loaded.assignedModerator()).contains(moderator);
    assertThat(loaded.firstResponseAt()).isPresent();
    assertThat(loaded.openedAt()).isEqualTo(CLOCK.instant());
  }

  @Test
  void aClosedCaseFreesTheTargetForAFreshOne() {
    ModerationTargetRef target = review();
    ModerationCase first = openCase(target, CaseTrigger.REPORT, RiskLevel.STANDARD);
    first.assignTo(ModeratorId.of(UUID.randomUUID()), CLOCK);
    first.markDecided(CLOCK);
    first.close(CLOCK);
    cases.save(first);

    assertThat(cases.findLiveByTarget(target)).isEmpty();
    ModerationCase second = openCase(target, CaseTrigger.REPORT, RiskLevel.STANDARD);
    assertThat(cases.findLiveByTarget(target).orElseThrow().id()).isEqualTo(second.id());
  }

  @Test
  void aReportSurvivesARoundTripAndItsDescriptionIsPreserved() {
    ModerationTargetRef target = review();
    ModerationCase opened = openCase(target, CaseTrigger.REPORT, RiskLevel.STANDARD);
    ReporterId reporter = ReporterId.of(UUID.randomUUID());
    Report report =
        Report.file(
            ReportId.of(UUID.randomUUID()),
            target,
            reporter,
            ReportCategory.OTHER,
            "Posted our door code.",
            CLOCK);
    report.linkTo(opened.id());
    reports.create(report);

    Report loaded = reports.findLive(reporter, target).orElseThrow();

    assertThat(loaded.id()).isEqualTo(report.id());
    assertThat(loaded.category()).isEqualTo(ReportCategory.OTHER);
    assertThat(loaded.description()).contains("Posted our door code.");
    assertThat(loaded.status()).isEqualTo(ReportStatus.LINKED);
    assertThat(loaded.caseId()).contains(opened.id());
  }

  @Test
  void aClosedOutReportStopsBlockingTheSameReporter() {
    ModerationTargetRef target = review();
    ModerationCase opened = openCase(target, CaseTrigger.REPORT, RiskLevel.STANDARD);
    ReporterId reporter = ReporterId.of(UUID.randomUUID());
    Report report = fileReport(target, reporter, opened.id());
    report.dismiss();
    reports.save(report);

    assertThat(reports.findLive(reporter, target)).isEmpty();
  }

  @Test
  void decisionsAccumulateRatherThanReplaceEachOther() {
    ModerationCase opened = openCase(review(), CaseTrigger.REPORT, RiskLevel.STANDARD);
    ModeratorId moderator = ModeratorId.of(UUID.randomUUID());
    decisions.append(
        decision(opened.id(), DecisionAction.ESCALATE, "LEGAL_REVIEW", null, moderator, CLOCK));
    decisions.append(
        decision(
            opened.id(),
            DecisionAction.REMOVE,
            "DOXXING",
            "Named a neighbour.",
            moderator,
            Clock.fixed(CLOCK.instant().plusSeconds(3600), ZoneOffset.UTC)));

    List<ModerationDecision> stored = decisions.findByCase(opened.id());

    // An appeal must be able to see the whole history, including a decision that was superseded.
    assertThat(stored).hasSize(2);
    assertThat(stored)
        .extracting(ModerationDecision::action)
        .containsExactly(DecisionAction.ESCALATE, DecisionAction.REMOVE);
    assertThat(stored.get(1).publicExplanation()).contains("Named a neighbour.");
    assertThat(stored.get(1).policyVersion()).isEqualTo(PolicyVersion.of(3));
  }

  @Test
  void theInternalNoteIsStoredButIsNotTheUserFacingExplanation() {
    ModerationCase opened = openCase(review(), CaseTrigger.REPORT, RiskLevel.STANDARD);
    decisions.append(
        ModerationDecision.record(
            ModerationDecisionId.of(UUID.randomUUID()),
            opened.id(),
            DecisionAction.HIDE,
            ReasonCode.of("PRIVACY_RISK"),
            PolicyVersion.of(3),
            "Pending redaction.",
            "matched detector v3",
            9L,
            ModeratorId.of(UUID.randomUUID()),
            CLOCK));

    ModerationDecision stored = decisions.findByCase(opened.id()).getFirst();

    assertThat(stored.publicExplanation()).contains("Pending redaction.");
    assertThat(stored.internalNote()).contains("matched detector v3");
    assertThat(stored.affectedTargetVersion()).contains(9L);
  }

  @Test
  void twoReportsRacingToOpenTheSameCaseLeaveExactlyOne() throws Exception {
    ModerationTargetRef target = review();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Throwable> first = executor.submit(() -> openConcurrently(target, ready, start));
      Future<Throwable> second = executor.submit(() -> openConcurrently(target, ready, start));
      ready.await();
      start.countDown();

      List<Throwable> outcomes = Arrays.asList(first.get(), second.get());
      // One wins; the other is told the case already exists rather than getting a raw constraint
      // error, which is what lets intake attach its report to the winner.
      assertThat(outcomes).filteredOn(value -> value == null).hasSize(1);
      assertThat(outcomes)
          .filteredOn(ModerationCaseAlreadyOpenException.class::isInstance)
          .hasSize(1);
    }

    assertThat(cases.findLiveByTarget(target)).isPresent();
  }

  @Test
  void twoReportsFromOneAccountRacingLeaveExactlyOne() throws Exception {
    ModerationTargetRef target = review();
    ModerationCaseId caseId = openCase(target, CaseTrigger.REPORT, RiskLevel.STANDARD).id();
    ReporterId reporter = ReporterId.of(UUID.randomUUID());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Throwable> first =
          executor.submit(() -> reportConcurrently(target, reporter, caseId, ready, start));
      Future<Throwable> second =
          executor.submit(() -> reportConcurrently(target, reporter, caseId, ready, start));
      ready.await();
      start.countDown();

      List<Throwable> outcomes = Arrays.asList(first.get(), second.get());
      assertThat(outcomes).filteredOn(value -> value == null).hasSize(1);
      assertThat(outcomes)
          .filteredOn(
              com.example.geohousing.moderation.application.DuplicateReportException.class
                  ::isInstance)
          .hasSize(1);
    }
  }

  @Test
  void savingACaseThatWasNeverCreatedIsRefusedRatherThanSilentlyInserting() {
    ModerationCase never =
        ModerationCase.open(
            ModerationCaseId.of(UUID.randomUUID()),
            review(),
            CaseTrigger.REPORT,
            RiskLevel.STANDARD,
            CLOCK);

    // Spring's transaction proxy translates the type, so assert the behaviour that matters:
    // refused, with a message that says why, rather than a silent insert.
    assertThatThrownBy(() -> cases.save(never)).hasMessageContaining("never created");
    assertThat(cases.findById(never.id())).isEmpty();
  }

  private Throwable openConcurrently(
      ModerationTargetRef target, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
      cases.create(
          ModerationCase.open(
              ModerationCaseId.of(UUID.randomUUID()),
              target,
              CaseTrigger.REPORT,
              RiskLevel.STANDARD,
              CLOCK));
      return null;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return exception;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private Throwable reportConcurrently(
      ModerationTargetRef target,
      ReporterId reporter,
      ModerationCaseId caseId,
      CountDownLatch ready,
      CountDownLatch start) {
    ready.countDown();
    try {
      start.await();
      fileReport(target, reporter, caseId);
      return null;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return exception;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private Report fileReport(
      ModerationTargetRef target, ReporterId reporter, ModerationCaseId caseId) {
    Report report =
        Report.file(
            ReportId.of(UUID.randomUUID()),
            target,
            reporter,
            ReportCategory.PERSONAL_DATA,
            null,
            CLOCK);
    report.linkTo(caseId);
    reports.create(report);
    return report;
  }

  private ModerationDecision decision(
      ModerationCaseId caseId,
      DecisionAction action,
      String reasonCode,
      String publicExplanation,
      ModeratorId moderator,
      Clock clock) {
    return ModerationDecision.record(
        ModerationDecisionId.of(UUID.randomUUID()),
        caseId,
        action,
        ReasonCode.of(reasonCode),
        PolicyVersion.of(3),
        publicExplanation,
        null,
        1L,
        moderator,
        clock);
  }

  private ModerationCase openCase(
      ModerationTargetRef target, CaseTrigger trigger, RiskLevel riskLevel) {
    ModerationCase opened =
        ModerationCase.open(
            ModerationCaseId.of(UUID.randomUUID()), target, trigger, riskLevel, CLOCK);
    cases.create(opened);
    return opened;
  }

  private static ModerationTargetRef review() {
    return ModerationTargetRef.review(UUID.randomUUID());
  }
}

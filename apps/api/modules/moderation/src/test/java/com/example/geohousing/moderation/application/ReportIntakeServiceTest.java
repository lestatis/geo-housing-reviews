package com.example.geohousing.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.api.AccountStanding;
import com.example.geohousing.moderation.domain.CaseTrigger;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportStatus;
import com.example.geohousing.moderation.domain.ReporterId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportIntakeServiceTest {

  /** Nobody is restricted unless a test says so. */
  private static final AccountStanding UNRESTRICTED = accountId -> false;

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryReportRepository reports = new InMemoryReportRepository();
  private final InMemoryModerationCaseRepository cases = new InMemoryModerationCaseRepository();
  private final InMemoryModerationTargetLookup targets = new InMemoryModerationTargetLookup();
  private final ReportIntakeService intake =
      new ReportIntakeService(reports, cases, targets, UNRESTRICTED, CLOCK);

  @Test
  void aReportOpensACaseForContentNothingHasBeenRaisedAboutYet() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 3L);

    Report report =
        intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, "It names my neighbour.");

    assertThat(report.status()).isEqualTo(ReportStatus.LINKED);
    assertThat(cases.byId).hasSize(1);
    ModerationCase opened = cases.byId.values().iterator().next();
    assertThat(opened.target()).isEqualTo(target);
    assertThat(opened.trigger()).isEqualTo(CaseTrigger.REPORT);
    assertThat(opened.status()).isEqualTo(ModerationCaseStatus.OPEN);
    assertThat(report.caseId()).contains(opened.id());
  }

  @Test
  void manyReportsAboutOneTargetConvergeOnTheSameCase() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);

    Report first = intake.file(reporter(), target, ReportCategory.HARASSMENT_OR_THREAT, null);
    Report second = intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);
    Report third = intake.file(reporter(), target, ReportCategory.DUPLICATE_OR_SPAM, null);

    // Otherwise an organised group opens a case per account and buries the queue.
    assertThat(cases.byId).hasSize(1);
    assertThat(first.caseId()).isEqualTo(second.caseId()).isEqualTo(third.caseId());
  }

  @Test
  void reportsAboutDifferentTargetsGetTheirOwnCases() {
    ModerationTargetRef one = targets.givenReviewBy(UUID.randomUUID(), 1L);
    ModerationTargetRef other = targets.givenReviewBy(UUID.randomUUID(), 1L);

    intake.file(reporter(), one, ReportCategory.PERSONAL_DATA, null);
    intake.file(reporter(), other, ReportCategory.PERSONAL_DATA, null);

    assertThat(cases.byId).hasSize(2);
  }

  @Test
  void aReportAboutContentThatDoesNotExistIsRefusedWithoutOpeningACase() {
    assertThatThrownBy(
            () ->
                intake.file(
                    reporter(),
                    ModerationTargetRef.review(UUID.randomUUID()),
                    ReportCategory.PERSONAL_DATA,
                    null))
        .isInstanceOf(ModerationTargetNotFoundException.class);

    // A refused report must leave no trace, or the queue becomes a way to probe for content.
    assertThat(cases.byId).isEmpty();
    assertThat(reports.byId).isEmpty();
  }

  @Test
  void contentTheReporterCouldNotHaveSeenIsReportedAsMissing() {
    // A review awaiting moderation, or already withdrawn, must not be confirmed to exist by the
    // reporting endpoint. Otherwise anyone could probe identifiers to discover unpublished content.
    ModerationTargetRef hidden = targets.givenReview(UUID.randomUUID(), 1L, false);

    assertThatThrownBy(() -> intake.file(reporter(), hidden, ReportCategory.PERSONAL_DATA, null))
        .isInstanceOf(ModerationTargetNotFoundException.class);

    assertThat(cases.byId).isEmpty();
    assertThat(reports.byId).isEmpty();
  }

  @Test
  void anAuthorCannotReportTheirOwnContent() {
    UUID author = UUID.randomUUID();
    ModerationTargetRef target = targets.givenReviewBy(author, 1L);

    // Not a report: an author who wants their own content gone edits or removes it.
    assertThatThrownBy(
            () ->
                intake.file(
                    ReporterId.of(author), target, ReportCategory.FALSE_OR_MISLEADING, null))
        .isInstanceOf(SelfReportNotAllowedException.class);

    assertThat(cases.byId).isEmpty();
    assertThat(reports.byId).isEmpty();
  }

  @Test
  void oneAccountCannotRaiseTheSameConcernRepeatedly() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    ReporterId reporter = reporter();
    intake.file(reporter, target, ReportCategory.PERSONAL_DATA, null);

    assertThatThrownBy(
            () -> intake.file(reporter, target, ReportCategory.HARASSMENT_OR_THREAT, null))
        .isInstanceOf(DuplicateReportException.class);

    assertThat(reports.byId).hasSize(1);
  }

  @Test
  void anotherAccountRaisingTheSameConcernIsExactlyWhatTheQueueNeedsToSee() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);

    Report second = intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);

    assertThat(second.status()).isEqualTo(ReportStatus.LINKED);
    assertThat(reports.byId).hasSize(2);
  }

  @Test
  void aClosedOutReportLetsTheSameAccountRaiseSomethingNewLater() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    ReporterId reporter = reporter();
    Report first = intake.file(reporter, target, ReportCategory.PERSONAL_DATA, null);
    first.dismiss();
    reports.save(first);

    Report again = intake.file(reporter, target, ReportCategory.OUTDATED_OR_RESOLVED, null);

    assertThat(again.status()).isEqualTo(ReportStatus.LINKED);
    assertThat(reports.byId).hasSize(2);
  }

  @Test
  void aReportArrivingWhileACaseIsBeingWorkedJoinsThatCase() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);
    Report first = intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null);
    ModerationCase open = cases.byId.values().iterator().next();
    open.assignTo(ModeratorId.of(UUID.randomUUID()), CLOCK);
    cases.save(open);

    Report second = intake.file(reporter(), target, ReportCategory.HARASSMENT_OR_THREAT, null);

    assertThat(second.caseId()).isEqualTo(first.caseId());
    assertThat(cases.byId).hasSize(1);
  }

  @Test
  void anOtherReportWithoutAnExplanationIsRefused() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);

    assertThatThrownBy(() -> intake.file(reporter(), target, ReportCategory.OTHER, "  "))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(reports.byId).isEmpty();
  }

  @Test
  void theReportRecordsWhatWasSaidAndWhenItArrived() {
    ModerationTargetRef target = targets.givenReviewBy(UUID.randomUUID(), 1L);

    Report report =
        intake.file(reporter(), target, ReportCategory.OTHER, "  Posted our door code.  ");

    assertThat(report.description()).contains("Posted our door code.");
    assertThat(report.createdAt()).isEqualTo(Instant.parse("2026-07-28T10:00:00Z"));
    assertThat(report.category()).isEqualTo(ReportCategory.OTHER);
  }

  private static ReporterId reporter() {
    return ReporterId.of(UUID.randomUUID());
  }
}

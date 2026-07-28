package com.example.geohousing.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC);

  @Test
  void aNewReportIsUnattachedAndHoldsItsReportersSlot() {
    Report report = file(ReportCategory.PERSONAL_DATA, null);

    assertThat(report.status()).isEqualTo(ReportStatus.OPEN);
    assertThat(report.caseId()).isEmpty();
    assertThat(report.isLive()).isTrue();
  }

  @Test
  void anOtherReportMustSayWhatIsWrong() {
    // "Other" with nothing written gives a moderator nothing to act on.
    assertThatThrownBy(() -> file(ReportCategory.OTHER, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("describe");
    assertThatThrownBy(() -> file(ReportCategory.OTHER, "   "))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(file(ReportCategory.OTHER, "Posted our building door code.").description())
        .contains("Posted our building door code.");
  }

  @Test
  void everyOtherCategoryAlreadyStatesTheProblem() {
    for (ReportCategory category : ReportCategory.values()) {
      if (category != ReportCategory.OTHER) {
        assertThat(file(category, null).description()).as("%s", category).isEmpty();
      }
    }
  }

  @Test
  void attachingAReportToItsCaseMovesItPastIntake() {
    Report report = file(ReportCategory.HARASSMENT_OR_THREAT, null);
    ModerationCaseId caseId = ModerationCaseId.of(UUID.randomUUID());

    report.linkTo(caseId);

    assertThat(report.status()).isEqualTo(ReportStatus.LINKED);
    assertThat(report.caseId()).contains(caseId);
    assertThat(report.isLive()).isTrue();
  }

  @Test
  void aClosedOutReportReleasesItsReportersSlot() {
    Report resolved = linked();
    resolved.resolve();
    assertThat(resolved.status()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(resolved.isLive()).isFalse();

    Report dismissed = linked();
    dismissed.dismiss();
    assertThat(dismissed.status()).isEqualTo(ReportStatus.DISMISSED);
    assertThat(dismissed.isLive()).isFalse();
  }

  @Test
  void aReportIsClosedOutOnlyOnceAndOnlyAfterItReachedACase() {
    Report report = file(ReportCategory.DUPLICATE_OR_SPAM, null);

    // Closing an unlinked report would leave no record of what it fed into.
    assertThatThrownBy(report::resolve)
        .isInstanceOf(IllegalModerationStateTransitionException.class);
    assertThatThrownBy(report::dismiss)
        .isInstanceOf(IllegalModerationStateTransitionException.class);

    report.linkTo(ModerationCaseId.of(UUID.randomUUID()));
    report.dismiss();
    assertThatThrownBy(report::resolve)
        .isInstanceOf(IllegalModerationStateTransitionException.class);
  }

  @Test
  void aReportIsAttachedToOneCaseOnly() {
    Report report = linked();

    assertThatThrownBy(() -> report.linkTo(ModerationCaseId.of(UUID.randomUUID())))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
  }

  @Test
  void persistedStateThatContradictsTheInvariantsIsRefused() {
    assertThatThrownBy(
            () ->
                Report.reconstitute(
                    ReportId.of(UUID.randomUUID()),
                    ModerationTargetRef.review(UUID.randomUUID()),
                    ReporterId.of(UUID.randomUUID()),
                    ReportCategory.PERSONAL_DATA,
                    null,
                    ReportStatus.LINKED,
                    null,
                    Instant.parse("2026-07-28T09:00:00Z")))
        .isInstanceOf(IllegalStateException.class);
  }

  private static Report linked() {
    Report report = file(ReportCategory.PERSONAL_DATA, null);
    report.linkTo(ModerationCaseId.of(UUID.randomUUID()));
    return report;
  }

  private static Report file(ReportCategory category, String description) {
    return Report.file(
        ReportId.of(UUID.randomUUID()),
        ModerationTargetRef.review(UUID.randomUUID()),
        ReporterId.of(UUID.randomUUID()),
        category,
        description,
        CLOCK);
  }
}

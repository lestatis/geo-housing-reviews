package com.example.geohousing.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationCaseTest {

  private static final Instant T0 = Instant.parse("2026-07-28T09:00:00Z");
  private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

  @Test
  void aNewCaseIsUnassignedAndLive() {
    ModerationCase moderationCase = openCase();

    assertThat(moderationCase.status()).isEqualTo(ModerationCaseStatus.OPEN);
    assertThat(moderationCase.assignedModerator()).isEmpty();
    assertThat(moderationCase.firstResponseAt()).isEmpty();
    assertThat(moderationCase.isLive()).isTrue();
    assertThat(moderationCase.acceptsReports()).isTrue();
  }

  @Test
  void takingACaseStampsWhenAHumanFirstLooked() {
    ModerationCase moderationCase = openCase();

    moderationCase.assignTo(moderator(), Clock.fixed(T0.plusSeconds(600), ZoneOffset.UTC));

    assertThat(moderationCase.status()).isEqualTo(ModerationCaseStatus.IN_REVIEW);
    assertThat(moderationCase.firstResponseAt()).contains(T0.plusSeconds(600));
  }

  @Test
  void reassigningKeepsTheOriginalFirstResponseTime() {
    ModerationCase moderationCase = openCase();
    moderationCase.assignTo(moderator(), Clock.fixed(T0.plusSeconds(600), ZoneOffset.UTC));

    // A moderator recognising a conflict of interest must be able to hand the case on
    // (MODERATION.md), but that handover is not a second "first response" — the reporter waited
    // once, and the SLA measures that wait.
    ModeratorId second = moderator();
    moderationCase.assignTo(second, Clock.fixed(T0.plus(Duration.ofHours(4)), ZoneOffset.UTC));

    assertThat(moderationCase.assignedModerator()).contains(second);
    assertThat(moderationCase.firstResponseAt()).contains(T0.plusSeconds(600));
  }

  @Test
  void aDecisionRequiresAModeratorAccountableForIt() {
    ModerationCase moderationCase = openCase();

    // No anonymous outcomes: an unassigned case cannot produce a decision.
    assertThatThrownBy(() -> moderationCase.markDecided(CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class)
        .hasMessageContaining("IN_REVIEW");
  }

  @Test
  void aCaseRunsFromOpenThroughDecisionAndAppealToClosed() {
    ModerationCase moderationCase = openCase();
    moderationCase.assignTo(moderator(), CLOCK);
    moderationCase.markDecided(CLOCK);
    assertThat(moderationCase.status()).isEqualTo(ModerationCaseStatus.DECIDED);
    assertThat(moderationCase.acceptsReports()).isFalse();

    moderationCase.markAppealed(CLOCK);
    assertThat(moderationCase.status()).isEqualTo(ModerationCaseStatus.APPEALED);

    Clock later = Clock.fixed(T0.plus(Duration.ofDays(2)), ZoneOffset.UTC);
    moderationCase.close(later);
    assertThat(moderationCase.status()).isEqualTo(ModerationCaseStatus.CLOSED);
    assertThat(moderationCase.closedAt()).contains(T0.plus(Duration.ofDays(2)));
    assertThat(moderationCase.isLive()).isFalse();
  }

  @Test
  void aCaseCannotBeClosedBeforeItHasBeenDecided() {
    ModerationCase moderationCase = openCase();

    // Nothing may let a case vanish unexplained: the affected user is always owed a recorded
    // reason, which is also what makes an appeal possible at all.
    assertThatThrownBy(() -> moderationCase.close(CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);

    moderationCase.assignTo(moderator(), CLOCK);
    assertThatThrownBy(() -> moderationCase.close(CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
  }

  @Test
  void anUndecidedCaseCannotBeAppealed() {
    ModerationCase moderationCase = openCase();
    moderationCase.assignTo(moderator(), CLOCK);

    assertThatThrownBy(() -> moderationCase.markAppealed(CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
  }

  @Test
  void aClosedCaseAcceptsNoFurtherWork() {
    ModerationCase moderationCase = closedCase();

    assertThatThrownBy(() -> moderationCase.assignTo(moderator(), CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
    assertThatThrownBy(() -> moderationCase.markDecided(CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
    assertThatThrownBy(() -> moderationCase.reclassify(RiskLevel.HIGH, CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
  }

  @Test
  void aLiveCaseCanBeReclassifiedAsMoreSerious() {
    ModerationCase moderationCase = openCase();

    moderationCase.reclassify(RiskLevel.LEGAL, CLOCK);

    assertThat(moderationCase.riskLevel()).isEqualTo(RiskLevel.LEGAL);
  }

  @Test
  void aTakedownDemandIsAnOrdinaryCase() {
    // MODERATION.md anti-capture: an owner's or developer's demand goes through the same audited
    // workflow as anything else, rather than a private channel.
    ModerationCase moderationCase =
        ModerationCase.open(
            ModerationCaseId.of(UUID.randomUUID()),
            ModerationTargetRef.review(UUID.randomUUID()),
            CaseTrigger.LEGAL_REQUEST,
            RiskLevel.LEGAL,
            CLOCK);

    assertThat(moderationCase.trigger()).isEqualTo(CaseTrigger.LEGAL_REQUEST);
    assertThat(moderationCase.status()).isEqualTo(ModerationCaseStatus.OPEN);
  }

  @Test
  void everyTransitionRecordsWhenItHappened() {
    // Mutation testing found this gap: nothing asserted that a transition stamps updatedAt, so
    // dropping the stamp entirely would have gone unnoticed — and a case whose updatedAt never
    // moves is one no queue can sort by "least recently touched".
    ModerationCase moderationCase = openCase();
    assertThat(moderationCase.updatedAt()).isEqualTo(T0);

    moderationCase.assignTo(moderator(), at(60));
    assertThat(moderationCase.updatedAt()).isEqualTo(T0.plusSeconds(60));

    moderationCase.reclassify(RiskLevel.HIGH, at(120));
    assertThat(moderationCase.updatedAt()).isEqualTo(T0.plusSeconds(120));

    moderationCase.markDecided(at(180));
    assertThat(moderationCase.updatedAt()).isEqualTo(T0.plusSeconds(180));

    moderationCase.markAppealed(at(240));
    assertThat(moderationCase.updatedAt()).isEqualTo(T0.plusSeconds(240));

    moderationCase.close(at(300));
    assertThat(moderationCase.updatedAt()).isEqualTo(T0.plusSeconds(300));
  }

  @Test
  void aCaseRemembersWhatItWasOpenedWith() {
    ModerationCaseId id = ModerationCaseId.of(UUID.randomUUID());
    ModerationTargetRef target = ModerationTargetRef.review(UUID.randomUUID());

    ModerationCase moderationCase =
        ModerationCase.open(id, target, CaseTrigger.AUTOMATED, RiskLevel.HIGH, CLOCK);

    assertThat(moderationCase.id()).isEqualTo(id);
    assertThat(moderationCase.target()).isEqualTo(target);
    assertThat(moderationCase.trigger()).isEqualTo(CaseTrigger.AUTOMATED);
    assertThat(moderationCase.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(moderationCase.openedAt()).isEqualTo(T0);
    assertThat(moderationCase.createdAt()).isEqualTo(T0);
    assertThat(moderationCase.version()).isZero();
  }

  @Test
  void persistedStateThatContradictsTheInvariantsIsRefused() {
    // A reconstitute that accepted anything would let a corrupt row become a live aggregate.
    assertThatThrownBy(
            () ->
                ModerationCase.reconstitute(
                    ModerationCaseId.of(UUID.randomUUID()),
                    ModerationTargetRef.review(UUID.randomUUID()),
                    CaseTrigger.REPORT,
                    ModerationCaseStatus.CLOSED,
                    RiskLevel.STANDARD,
                    moderator(),
                    T0,
                    T0,
                    null,
                    T0,
                    T0,
                    3L))
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(
            () ->
                ModerationCase.reconstitute(
                    ModerationCaseId.of(UUID.randomUUID()),
                    ModerationTargetRef.review(UUID.randomUUID()),
                    CaseTrigger.REPORT,
                    ModerationCaseStatus.DECIDED,
                    RiskLevel.STANDARD,
                    null,
                    T0,
                    T0,
                    null,
                    T0,
                    T0,
                    3L))
        .isInstanceOf(IllegalStateException.class);
  }

  private static ModerationCase openCase() {
    return ModerationCase.open(
        ModerationCaseId.of(UUID.randomUUID()),
        ModerationTargetRef.review(UUID.randomUUID()),
        CaseTrigger.REPORT,
        RiskLevel.STANDARD,
        CLOCK);
  }

  private static ModerationCase closedCase() {
    ModerationCase moderationCase = openCase();
    moderationCase.assignTo(moderator(), CLOCK);
    moderationCase.markDecided(CLOCK);
    moderationCase.close(CLOCK);
    return moderationCase;
  }

  private static ModeratorId moderator() {
    return ModeratorId.of(UUID.randomUUID());
  }

  /** A clock reading the given number of seconds after the scenario's start. */
  private static Clock at(int secondsAfterStart) {
    return Clock.fixed(T0.plusSeconds(secondsAfterStart), ZoneOffset.UTC);
  }
}

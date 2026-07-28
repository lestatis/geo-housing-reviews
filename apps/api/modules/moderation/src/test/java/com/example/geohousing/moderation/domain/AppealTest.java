package com.example.geohousing.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppealTest {

  private static final Instant T0 = Instant.parse("2026-07-28T09:00:00Z");
  private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

  @Test
  void anAppealRemembersWhoItIsAppealingAgainst() {
    ModeratorId original = moderator();
    Appeal appeal = fileAppealAgainst(original);

    // Carried on the appeal so the different-decider rule can be checked here, rather than by a
    // caller that happens to remember to look the decision up.
    assertThat(appeal.originalDecider()).isEqualTo(original);
    assertThat(appeal.status()).isEqualTo(AppealStatus.PENDING);
    assertThat(appeal.decidedAt()).isEmpty();
  }

  @Test
  void theModeratorBeingAppealedAgainstCannotHearTheAppeal() {
    ModeratorId original = moderator();
    Appeal appeal = fileAppealAgainst(original);

    assertThat(appeal.canBeDecidedBy(original)).isFalse();
    assertThatThrownBy(() -> appeal.uphold(original, "I stand by it.", CLOCK))
        .isInstanceOf(AppealDeciderConflictException.class);
    assertThatThrownBy(() -> appeal.overturn(original, "On reflection, no.", CLOCK))
        .isInstanceOf(AppealDeciderConflictException.class);

    // Refused before anything moved: the appeal is still waiting to be heard properly.
    assertThat(appeal.status()).isEqualTo(AppealStatus.PENDING);
    assertThat(appeal.decidedBy()).isEmpty();
  }

  @Test
  void aDifferentModeratorCanOverturnTheDecision() {
    Appeal appeal = fileAppealAgainst(moderator());
    ModeratorId second = moderator();
    Clock later = Clock.fixed(T0.plusSeconds(86_400), ZoneOffset.UTC);

    assertThat(appeal.canBeDecidedBy(second)).isTrue();
    appeal.overturn(second, "Restored: the flat was the author's own.", later);

    assertThat(appeal.status()).isEqualTo(AppealStatus.OVERTURNED);
    assertThat(appeal.decidedBy()).contains(second);
    assertThat(appeal.decidedAt()).contains(T0.plusSeconds(86_400));
    assertThat(appeal.outcomeExplanation()).contains("Restored: the flat was the author's own.");
  }

  @Test
  void anUpheldAppealStillOwesTheAppellantAReason() {
    Appeal appeal = fileAppealAgainst(moderator());

    assertThatThrownBy(() -> appeal.uphold(moderator(), null, CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> appeal.uphold(moderator(), "   ", CLOCK))
        .isInstanceOf(IllegalArgumentException.class);

    appeal.uphold(moderator(), "The flat number identified a neighbour, not you.", CLOCK);
    assertThat(appeal.status()).isEqualTo(AppealStatus.UPHELD);
  }

  @Test
  void anAppealIsHeardOnce() {
    Appeal appeal = fileAppealAgainst(moderator());
    appeal.uphold(moderator(), "Reviewed again; the decision stands.", CLOCK);

    // A channel that can be worked repeatedly is one an organised party uses to wear moderation
    // down, so a second attempt is refused rather than queued.
    assertThat(appeal.canBeDecidedBy(moderator())).isFalse();
    assertThatThrownBy(() -> appeal.overturn(moderator(), "Actually, no.", CLOCK))
        .isInstanceOf(IllegalModerationStateTransitionException.class);
    assertThat(appeal.status()).isEqualTo(AppealStatus.UPHELD);
  }

  @Test
  void anAppealMustSayWhatItIsAppealing() {
    assertThatThrownBy(() -> fileAppealWithText(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> fileAppealWithText("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void persistedStateThatContradictsDueProcessIsRefused() {
    ModeratorId original = moderator();

    // A row where the appealed-against moderator decided their own appeal must not load.
    assertThatThrownBy(
            () ->
                Appeal.reconstitute(
                    AppealId.of(UUID.randomUUID()),
                    ModerationDecisionId.of(UUID.randomUUID()),
                    AppellantId.of(UUID.randomUUID()),
                    "text",
                    AppealStatus.UPHELD,
                    "outcome",
                    original,
                    original,
                    T0,
                    T0,
                    1L))
        .isInstanceOf(IllegalStateException.class);

    // Nor one that claims to be decided without recording when.
    assertThatThrownBy(
            () ->
                Appeal.reconstitute(
                    AppealId.of(UUID.randomUUID()),
                    ModerationDecisionId.of(UUID.randomUUID()),
                    AppellantId.of(UUID.randomUUID()),
                    "text",
                    AppealStatus.OVERTURNED,
                    "outcome",
                    original,
                    moderator(),
                    T0,
                    null,
                    1L))
        .isInstanceOf(IllegalStateException.class);
  }

  private static Appeal fileAppealAgainst(ModeratorId originalDecider) {
    return Appeal.file(
        AppealId.of(UUID.randomUUID()),
        removalBy(originalDecider),
        AppellantId.of(UUID.randomUUID()),
        "The flat number was my own.",
        CLOCK);
  }

  private static Appeal fileAppealWithText(String text) {
    return Appeal.file(
        AppealId.of(UUID.randomUUID()),
        removalBy(moderator()),
        AppellantId.of(UUID.randomUUID()),
        text,
        CLOCK);
  }

  private static ModerationDecision removalBy(ModeratorId moderatorId) {
    return ModerationDecision.record(
        ModerationDecisionId.of(UUID.randomUUID()),
        ModerationCaseId.of(UUID.randomUUID()),
        DecisionAction.REMOVE,
        ReasonCode.of("DOXXING"),
        PolicyVersion.of(1),
        "Your review identified a neighbour.",
        null,
        2L,
        moderatorId,
        CLOCK);
  }

  private static ModeratorId moderator() {
    return ModeratorId.of(UUID.randomUUID());
  }
}

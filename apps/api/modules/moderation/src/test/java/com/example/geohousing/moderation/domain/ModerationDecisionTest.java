package com.example.geohousing.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationDecisionTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC);

  @Test
  void anythingThatCostsTheUserSomethingMustExplainWhy() {
    for (DecisionAction action : DecisionAction.values()) {
      if (action.requiresPublicExplanation()) {
        assertThatThrownBy(() -> record(action, null, null))
            .as("%s", action)
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record(action, "   ", null))
            .as("%s", action)
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
  }

  @Test
  void approvingAndEscalatingOweTheAuthorNoExplanation() {
    // APPROVE takes nothing away; ESCALATE is an internal handoff that has decided nothing yet.
    assertThatCode(() -> record(DecisionAction.APPROVE, null, null)).doesNotThrowAnyException();
    assertThatCode(() -> record(DecisionAction.ESCALATE, null, "for legal review"))
        .doesNotThrowAnyException();

    assertThat(DecisionAction.ESCALATE.isConclusive()).isFalse();
    assertThat(DecisionAction.REMOVE.isConclusive()).isTrue();
  }

  @Test
  void theUserFacingAndModeratorOnlyExplanationsAreSeparateFields() {
    ModerationDecision decision =
        record(
            DecisionAction.REMOVE,
            "Your review named a neighbour and their flat.",
            "matched doxxing detector; reporter is unrelated to the author");

    // Keeping these apart is the point: MODERATION.md wants an explanation specific enough to fix
    // the problem, without telling anyone how detection works.
    assertThat(decision.publicExplanation())
        .contains("Your review named a neighbour and their flat.");
    assertThat(decision.internalNote())
        .contains("matched doxxing detector; reporter is unrelated to the author");
  }

  @Test
  void aDecisionRecordsWhatItJudgedAndUnderWhichPolicy() {
    ModerationDecision decision =
        ModerationDecision.record(
            ModerationDecisionId.of(UUID.randomUUID()),
            ModerationCaseId.of(UUID.randomUUID()),
            DecisionAction.HIDE,
            ReasonCode.of("PRIVACY_RISK"),
            PolicyVersion.of(3),
            "Pending redaction of a flat number.",
            null,
            7L,
            ModeratorId.of(UUID.randomUUID()),
            CLOCK);

    // Without the judged version, an edit after the decision is indistinguishable from what the
    // moderator actually read; without the policy version, an appeal is judged by today's rules.
    assertThat(decision.affectedTargetVersion()).contains(7L);
    assertThat(decision.policyVersion()).isEqualTo(PolicyVersion.of(3));
    assertThat(decision.decidedAt()).isEqualTo(Instant.parse("2026-07-28T09:00:00Z"));
  }

  @Test
  void aDecisionAlwaysCarriesAReasonCode() {
    assertThatThrownBy(() -> ReasonCode.of(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReasonCode.of("  ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReasonCode.of("x".repeat(ReasonCode.MAX_LENGTH + 1)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(ReasonCode.of("  DOXXING  ").value()).isEqualTo("DOXXING");
  }

  @Test
  void aPolicyVersionIsAlwaysPositive() {
    assertThatThrownBy(() -> PolicyVersion.of(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PolicyVersion.of(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aDecisionExposesNoWayToChangeIt() {
    // Immutability is the property an appeal depends on: it must be able to show what was decided,
    // not what the decision later became. Guarded here so a mutator cannot be added unnoticed.
    assertThat(ModerationDecision.class.getMethods())
        .filteredOn(method -> method.getDeclaringClass() == ModerationDecision.class)
        .allSatisfy(
            method ->
                assertThat(method.getReturnType())
                    .as("ModerationDecision.%s must be a query, not a mutator", method.getName())
                    .isNotEqualTo(void.class));
  }

  private static ModerationDecision record(
      DecisionAction action, String publicExplanation, String internalNote) {
    return ModerationDecision.record(
        ModerationDecisionId.of(UUID.randomUUID()),
        ModerationCaseId.of(UUID.randomUUID()),
        action,
        ReasonCode.of("REASON"),
        PolicyVersion.of(1),
        publicExplanation,
        internalNote,
        1L,
        ModeratorId.of(UUID.randomUUID()),
        CLOCK);
  }
}

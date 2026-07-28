package com.example.geohousing.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Pins the invariants the ranking policy must hold, not the shape of its curve. The exact numbers
 * are policy parameters that a later version may change; what may not change without an explicit
 * argument is that the input stays bounded, rewards additional signals less and less, and cannot be
 * influenced by anything except the signal count.
 */
class HelpfulnessInputPolicyTest {

  @Test
  void theInputIsBoundedForEveryPossibleCount() {
    for (long count : new long[] {0L, 1L, 25L, 1_000L, Long.MAX_VALUE}) {
      assertThat(HelpfulnessInputPolicy.forActiveSignals(count).value())
          .as("count %d", count)
          .isBetween(0.0, 1.0);
    }
  }

  @Test
  void aReviewNobodyHasSignalledContributesNothing() {
    assertThat(HelpfulnessInputPolicy.forActiveSignals(0L).value()).isZero();
  }

  @Test
  void moreSignalsNeverScoreLower() {
    double previous = -1.0;
    for (long count = 0L; count <= 60L; count++) {
      double current = HelpfulnessInputPolicy.forActiveSignals(count).value();
      assertThat(current).as("count %d", count).isGreaterThanOrEqualTo(previous);
      previous = current;
    }
  }

  @Test
  void eachAdditionalSignalIsWorthNoMoreThanTheOneBeforeIt() {
    // Diminishing returns: the marginal value of the next signal never grows, so buying more votes
    // never becomes a better investment than it already was.
    for (long count = 1L; count <= 60L; count++) {
      double previousGain = gainAt(count - 1);
      double gain = gainAt(count);
      assertThat(gain).as("marginal gain at %d", count).isLessThanOrEqualTo(previousGain + 1e-12);
    }
  }

  @Test
  void aLargeCampaignScoresNoHigherThanAModestOne() {
    // The abuse bound. Past saturation the policy stops paying, so mustering 10,000 accounts wins
    // exactly what mustering 25 already won.
    double atSaturation = HelpfulnessInputPolicy.forActiveSignals(25L).value();
    double brigade = HelpfulnessInputPolicy.forActiveSignals(10_000L).value();

    assertThat(brigade).isEqualTo(atSaturation);
    assertThat(brigade).isEqualTo(1.0);
  }

  @Test
  void aFewHonestSignalsAlreadyCarryRealWeight() {
    // The flip side of saturation: if the curve only paid out near the cap, an honest review on a
    // small property could never register at all.
    assertThat(HelpfulnessInputPolicy.forActiveSignals(1L).value()).isGreaterThan(0.15);
    assertThat(HelpfulnessInputPolicy.forActiveSignals(5L).value()).isGreaterThan(0.5);
  }

  @Test
  void theInputDependsOnTheSignalCountAndNothingElse() {
    // The structural form of "paid status must not increase organic rank" (PRD_MVP.md §6): the
    // policy takes a count and a version, so there is no argument through which sponsorship, an
    // owner relationship, or a verification tier could reach it. Repeated calls agree because
    // nothing else is in scope to vary.
    HelpfulnessInput first = HelpfulnessInputPolicy.forActiveSignals(7L);
    HelpfulnessInput second = HelpfulnessInputPolicy.forActiveSignals(7L);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void aNegativeCountIsRejectedRatherThanScored() {
    assertThatThrownBy(() -> HelpfulnessInputPolicy.forActiveSignals(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("negative");
  }

  @Test
  void everyInputCarriesTheVersionThatProducedIt() {
    assertThat(HelpfulnessInputPolicy.forActiveSignals(3L).version())
        .isEqualTo(RankingInputVersion.current());
  }

  @Test
  void aHistoricalVersionCanBeReplayedForAudit() {
    // Replaying a stored count through a named version is how a past ranking decision is explained;
    // no score has to be stored for that to work.
    HelpfulnessInput replayed = HelpfulnessInputPolicy.forActiveSignals(9L, RankingInputVersion.V1);

    assertThat(replayed.version()).isEqualTo(RankingInputVersion.V1);
    assertThat(replayed.value()).isEqualTo(HelpfulnessInputPolicy.forActiveSignals(9L).value());
  }

  private static double gainAt(long count) {
    return HelpfulnessInputPolicy.forActiveSignals(count + 1).value()
        - HelpfulnessInputPolicy.forActiveSignals(count).value();
  }
}

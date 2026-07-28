package com.example.geohousing.reviews.domain;

import java.util.Objects;

/**
 * Turns a review's active helpful-signal count into a bounded ranking input.
 *
 * <p>The count itself is the wrong thing to rank on: it grows without limit, so a coordinated
 * cohort could outweigh every honest review on a property simply by being larger. This policy is
 * <em>saturating</em> — the first signals move the value most and past a threshold further signals
 * buy nothing at all — which caps what any campaign can win no matter how many accounts it musters.
 *
 * <p>The value is a pure function of the count and of nothing else. That is the structural form of
 * PRD_MVP.md §6's hard rule that paid status must not increase organic rank: sponsorship cannot
 * influence a calculation it is not an input to. Adding another argument here is therefore a change
 * that has to be argued against that rule, not a refactor.
 */
public final class HelpfulnessInputPolicy {

  // V1 parameters. The threshold is deliberately modest: on a launch-sized property a handful of
  // signals is already a strong reading, and the point of the cap is that the difference between a
  // brigade of 100 and one of 10,000 is nil. Changing these numbers changes what a recorded value
  // means, so it requires a new RankingInputVersion rather than an edit here.
  private static final long V1_SATURATION_SIGNALS = 25L;
  private static final double V1_SATURATION_SCALE = Math.log1p(V1_SATURATION_SIGNALS);

  private HelpfulnessInputPolicy() {}

  /** The input for a count, under the version new values are produced with. */
  public static HelpfulnessInput forActiveSignals(long activeSignals) {
    return forActiveSignals(activeSignals, RankingInputVersion.current());
  }

  /**
   * The input for a count under an explicitly chosen version.
   *
   * <p>This overload is what makes ranking auditable. The helpful-signal rows are append-only and
   * preserve withdrawals, so replaying a historical count through the version that was current at
   * the time reproduces exactly the input that was used then — no stored score is needed, and none
   * can drift away from the rows it summarises.
   *
   * @throws IllegalArgumentException if the count is negative
   */
  public static HelpfulnessInput forActiveSignals(long activeSignals, RankingInputVersion version) {
    Objects.requireNonNull(version, "version");
    if (activeSignals < 0) {
      throw new IllegalArgumentException(
          "active signal count cannot be negative: " + activeSignals);
    }
    return new HelpfulnessInput(scoreFor(activeSignals, version), version);
  }

  private static double scoreFor(long activeSignals, RankingInputVersion version) {
    return switch (version) {
      case V1 -> {
        if (activeSignals == 0L) {
          yield 0.0;
        }
        yield Math.min(1.0, Math.log1p((double) activeSignals) / V1_SATURATION_SCALE);
      }
    };
  }
}

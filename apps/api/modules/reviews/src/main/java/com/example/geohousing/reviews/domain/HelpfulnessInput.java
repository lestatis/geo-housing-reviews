package com.example.geohousing.reviews.domain;

import java.util.Objects;

/**
 * A review's helpfulness contribution to ranking: a bounded value in {@code [0, 1]} together with
 * the policy version that produced it.
 *
 * <p>Boundedness is a type invariant rather than a convention, because it is what keeps helpfulness
 * from dominating ranking. An unbounded input would let a large enough voting cohort outweigh every
 * other factor combined, which both PRD_MVP.md §6 ("helpfulness with abuse resistance") and
 * TRUST_VERIFICATION.md §5 (no single factor dominates) forbid. A ranking layer can therefore weigh
 * this against other inputs knowing its scale in advance.
 *
 * <p>This value is never part of a public representation. It is derived from the helpful-signal
 * count, and publishing it would let anyone recover the curve behind it by adding a signal and
 * watching the number move — the manipulable formula PRD_MVP.md §6 says never to expose.
 */
public record HelpfulnessInput(double value, RankingInputVersion version) {

  public HelpfulnessInput {
    Objects.requireNonNull(version, "version");
    if (!(value >= 0.0) || !(value <= 1.0)) {
      // Written as a range check rather than !(0 <= v <= 1) so NaN is rejected too.
      throw new IllegalArgumentException("helpfulness input must be within [0, 1], was " + value);
    }
  }

  /** The input for a review nothing has signalled yet. */
  public static HelpfulnessInput zero(RankingInputVersion version) {
    return new HelpfulnessInput(0.0, version);
  }
}

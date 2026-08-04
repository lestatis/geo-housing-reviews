package com.example.geohousing.moderation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModerationThroughputTest {

  @Test
  void aWindowsWorkIsDecisionsAppealsHeardAndAppealsOverturned() {
    ModerationThroughput throughput = new ModerationThroughput(87, 14, 3);

    assertThat(throughput.decisions()).isEqualTo(87);
    assertThat(throughput.appealsHeard()).isEqualTo(14);
    assertThat(throughput.appealsOverturned()).isEqualTo(3);
  }

  @Test
  void moreOverturnedThanHeardIsRefusedRatherThanReported() {
    // These are two separate queries. A wrong predicate in either would otherwise reach a screen
    // as an overturn rate above 100% — a number that discredits the whole page rather than
    // pointing at the bug that produced it.
    assertThatThrownBy(() -> new ModerationThroughput(10, 2, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyOverturnedAppealBeingOverturnedIsFine() {
    // The boundary is legitimate: a quiet window in which the single appeal heard was overturned.
    assertThat(new ModerationThroughput(1, 1, 1).appealsOverturned()).isEqualTo(1);
  }

  @Test
  void aNegativeCountIsNotACount() {
    assertThatThrownBy(() -> new ModerationThroughput(-1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ModerationThroughput(0, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ModerationThroughput(0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anEmptyWindowIsZeroesRatherThanAFailure() {
    assertThat(new ModerationThroughput(0, 0, 0).decisions()).isZero();
  }
}

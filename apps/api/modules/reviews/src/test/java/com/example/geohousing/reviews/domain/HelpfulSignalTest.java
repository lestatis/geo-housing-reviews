package com.example.geohousing.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HelpfulSignalTest {

  private static final Clock CREATED =
      Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);
  private static final Clock WITHDRAWN =
      Clock.fixed(Instant.parse("2026-07-27T11:00:00Z"), ZoneOffset.UTC);

  @Test
  void signalIsActiveUntilWithdrawnAndRetainsItsHistory() {
    HelpfulSignal signal = signal(CREATED);

    assertThat(signal.isActive()).isTrue();
    assertThat(signal.withdrawnAt()).isNull();

    signal.withdraw(WITHDRAWN);

    assertThat(signal.isActive()).isFalse();
    assertThat(signal.withdrawnAt()).isEqualTo(WITHDRAWN.instant());
    assertThat(signal.createdAt()).isEqualTo(CREATED.instant());
  }

  @Test
  void signalCannotBeWithdrawnTwiceOrBeforeItWasCreated() {
    HelpfulSignal signal = signal(CREATED);

    assertThatThrownBy(
            () -> signal.withdraw(Clock.offset(CREATED, java.time.Duration.ofSeconds(-1))))
        .isInstanceOf(IllegalArgumentException.class);

    signal.withdraw(WITHDRAWN);
    assertThatThrownBy(() -> signal.withdraw(WITHDRAWN)).isInstanceOf(IllegalStateException.class);
  }

  private HelpfulSignal signal(Clock clock) {
    return HelpfulSignal.create(
        HelpfulSignalId.of(UUID.randomUUID()),
        ReviewId.of(UUID.randomUUID()),
        HelpfulSignalVoterId.of(UUID.randomUUID()),
        clock);
  }
}

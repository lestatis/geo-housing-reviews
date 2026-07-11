package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserRestrictionTest {

  private static final Instant START = Instant.parse("2026-07-11T00:00:00Z");
  private static final Instant END = Instant.parse("2026-07-20T00:00:00Z");
  private static final AccountId ACCOUNT_ID = AccountId.of(UUID.randomUUID());

  private UserRestriction bounded() {
    return UserRestriction.reconstitute(
        UUID.randomUUID(),
        ACCOUNT_ID,
        RestrictionScope.ACCOUNT_WIDE,
        "spam",
        START,
        END,
        null,
        AppealStatus.NONE,
        START);
  }

  private UserRestriction indefinite() {
    return UserRestriction.reconstitute(
        UUID.randomUUID(),
        ACCOUNT_ID,
        RestrictionScope.ACCOUNT_WIDE,
        "abuse",
        START,
        null,
        null,
        AppealStatus.NONE,
        START);
  }

  @Test
  void inactiveBeforeStart() {
    assertThat(bounded().isActiveAt(START.minusSeconds(1))).isFalse();
  }

  @Test
  void activeAtStart() {
    assertThat(bounded().isActiveAt(START)).isTrue();
  }

  @Test
  void activeInsideWindow() {
    assertThat(bounded().isActiveAt(START.plusSeconds(3600))).isTrue();
  }

  @Test
  void inactiveAtEndBoundary() {
    assertThat(bounded().isActiveAt(END)).isFalse();
  }

  @Test
  void inactiveAfterEnd() {
    assertThat(bounded().isActiveAt(END.plusSeconds(1))).isFalse();
  }

  @Test
  void indefiniteRestrictionStaysActiveFarInTheFuture() {
    assertThat(indefinite().isActiveAt(START.plusSeconds(86_400L * 3650))).isTrue();
  }

  @Test
  void rejectsEndBeforeStart() {
    assertThatThrownBy(
            () ->
                UserRestriction.reconstitute(
                    UUID.randomUUID(),
                    ACCOUNT_ID,
                    RestrictionScope.ACCOUNT_WIDE,
                    "bad window",
                    END,
                    START,
                    null,
                    AppealStatus.NONE,
                    START))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

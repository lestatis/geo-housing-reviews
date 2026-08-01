package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

  @Test
  void placingARestrictionRecordsWhoDecidedItAndWhy() {
    Clock clock = Clock.fixed(START, ZoneOffset.UTC);
    AccountId moderator = AccountId.of(UUID.randomUUID());

    UserRestriction placed =
        UserRestriction.place(
            UUID.randomUUID(),
            ACCOUNT_ID,
            RestrictionScope.ACCOUNT_WIDE,
            "  posting a neighbour's phone number  ",
            END,
            moderator,
            clock);

    assertThat(placed.accountId()).isEqualTo(ACCOUNT_ID);
    assertThat(placed.reason()).isEqualTo("posting a neighbour's phone number");
    assertThat(placed.moderatorAccountId()).contains(moderator);
    assertThat(placed.startAt()).isEqualTo(START);
    assertThat(placed.endAt()).contains(END);
    assertThat(placed.createdAt()).isEqualTo(START);
    // Nothing has been appealed yet, and the restriction does not presume otherwise.
    assertThat(placed.appealStatus()).isEqualTo(AppealStatus.NONE);
  }

  @Test
  void aRestrictionWithNoEndIsIndefiniteRatherThanInvalid() {
    UserRestriction placed =
        UserRestriction.place(
            UUID.randomUUID(),
            ACCOUNT_ID,
            RestrictionScope.ACCOUNT_WIDE,
            "repeated coordinated posting",
            null,
            AccountId.of(UUID.randomUUID()),
            Clock.fixed(START, ZoneOffset.UTC));

    assertThat(placed.endAt()).isEmpty();
    assertThat(placed.isActiveAt(START.plusSeconds(86_400L * 3650))).isTrue();
  }

  @Test
  void everyRestrictionSaysWhy() {
    // A restriction the affected person cannot be told the reason for is one they cannot appeal or
    // correct. The schema requires it too; the aggregate refuses before the database has to.
    assertThatThrownBy(
            () ->
                UserRestriction.place(
                    UUID.randomUUID(),
                    ACCOUNT_ID,
                    RestrictionScope.ACCOUNT_WIDE,
                    "   ",
                    END,
                    AccountId.of(UUID.randomUUID()),
                    Clock.fixed(START, ZoneOffset.UTC)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRestrictionCannotEndBeforeItStarts() {
    assertThatThrownBy(
            () ->
                UserRestriction.place(
                    UUID.randomUUID(),
                    ACCOUNT_ID,
                    RestrictionScope.ACCOUNT_WIDE,
                    "spam",
                    START.minusSeconds(1),
                    AccountId.of(UUID.randomUUID()),
                    Clock.fixed(START, ZoneOffset.UTC)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRestrictionNamesTheModeratorWhoPlacedIt() {
    // Accountability runs both ways: the affected account is named on the row, and so is whoever
    // decided. An unattributed restriction is one nobody can be asked about.
    assertThatThrownBy(
            () ->
                UserRestriction.place(
                    UUID.randomUUID(),
                    ACCOUNT_ID,
                    RestrictionScope.ACCOUNT_WIDE,
                    "spam",
                    END,
                    null,
                    Clock.fixed(START, ZoneOffset.UTC)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void liftingEndsTheRestrictionWithoutErasingIt() {
    // A lifted restriction is history, not a mistake to be deleted — an appeal, or a later
    // moderator, needs to see that it happened and when it stopped.
    Instant lifted = START.plusSeconds(3600);
    UserRestriction restriction = bounded();

    UserRestriction ended = restriction.liftedAt(lifted);

    assertThat(ended.id()).isEqualTo(restriction.id());
    assertThat(ended.reason()).isEqualTo(restriction.reason());
    assertThat(ended.endAt()).contains(lifted);
    assertThat(ended.isActiveAt(lifted)).isFalse();
    assertThat(ended.isActiveAt(lifted.minusSeconds(1))).isTrue();
  }
}

package com.example.geohousing.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

  private static final Instant AT = Instant.parse("2026-08-04T10:00:00Z");
  private static final UUID ACTOR = UUID.randomUUID();

  @Test
  void anEntrySaysWhenWhoWhatAndOnWhat() {
    AuditEntry entry =
        new AuditEntry(AT, ACTOR, "identity", "GRANT_ADMIN", "ACCOUNT", "abc", "APPLIED", null);

    assertThat(entry.at()).isEqualTo(AT);
    assertThat(entry.actorAccountId()).contains(ACTOR);
    assertThat(entry.module()).isEqualTo("identity");
    assertThat(entry.action()).isEqualTo("GRANT_ADMIN");
    assertThat(entry.subjectType()).isEqualTo("ACCOUNT");
    assertThat(entry.subjectId()).contains("abc");
    assertThat(entry.outcome()).contains("APPLIED");
  }

  @Test
  void anActorIsOptionalBecauseSomeActionsAreTheSystemsOwn() {
    // Verification expires a lapsed badge on a schedule; nobody decided it, and recording a
    // fabricated actor would be worse than recording none.
    AuditEntry expiry =
        new AuditEntry(AT, null, "verification", "EXPIRE", "CASE", "c1", "APPLIED", "LAPSED");

    assertThat(expiry.actorAccountId()).isEmpty();
  }

  @Test
  void aReasonIsOptionalBecauseNotEveryActionCarriesOne() {
    AuditEntry view =
        new AuditEntry(AT, ACTOR, "identity", "VIEW_ACCOUNT", "ACCOUNT", "abc", "FOUND", null);

    assertThat(view.reason()).isEmpty();
    assertThat(
            new AuditEntry(AT, ACTOR, "reviews", "HIDE", "REVIEW", "r1", "APPLIED", "PRIVACY_RISK")
                .reason())
        .contains("PRIVACY_RISK");
  }

  @Test
  void anEntryAlwaysKnowsWhenItHappenedAndWhatHappened() {
    // A timeline sorted by a missing instant, or carrying a blank action, is not a record of
    // anything — it is a row that survived a mapping bug.
    assertThatThrownBy(
            () ->
                new AuditEntry(null, ACTOR, "identity", "GRANT_ADMIN", "ACCOUNT", "a", null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new AuditEntry(AT, ACTOR, "identity", "  ", "ACCOUNT", "a", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new AuditEntry(AT, ACTOR, "  ", "GRANT_ADMIN", "ACCOUNT", "a", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void newestFirstIsTheOrderATimelineIsReadIn() {
    AuditEntry older =
        new AuditEntry(AT, ACTOR, "identity", "VIEW_ACCOUNT", "ACCOUNT", "a", null, null);
    AuditEntry newer =
        new AuditEntry(AT.plusSeconds(60), ACTOR, "reviews", "HIDE", "REVIEW", "r", null, "SPAM");

    assertThat(java.util.stream.Stream.of(older, newer).sorted(AuditEntry.NEWEST_FIRST).toList())
        .containsExactly(newer, older);
  }
}

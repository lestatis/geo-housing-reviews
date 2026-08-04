package com.example.geohousing.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCursorTest {

  private static final Instant AT = Instant.parse("2026-08-04T10:00:00Z");
  private static final UUID ID = UUID.fromString("77777777-7777-4777-8777-777777777777");

  @Test
  void aSourceSharingTheCursorsModuleResumesAtTheCursorsOwnRow() {
    // Same instant, same module: only rows below the cursor's id are still to come.
    assertThat(new AuditCursor(AT, "identity", ID).idBoundFor("identity")).isEqualTo(ID);
  }

  @Test
  void aSourceSortingAfterTheCursorsModuleKeepsEverythingAtThatInstant() {
    // Ordering is (at desc, module asc, id desc). A module after the cursor's has not been read at
    // that instant at all, so none of its rows there may be skipped.
    assertThat(new AuditCursor(AT, "identity", ID).idBoundFor("properties"))
        .isEqualTo(AuditCursor.HIGHEST_ID);
  }

  @Test
  void aSourceSortingBeforeTheCursorsModuleIsDoneWithThatInstant() {
    // Everything it had at that instant was already returned; only strictly older rows remain.
    assertThat(new AuditCursor(AT, "properties", ID).idBoundFor("identity"))
        .isEqualTo(AuditCursor.LOWEST_ID);
  }

  @Test
  void theFirstPageStartsAtTheWindowsEndWithNothingExcluded() {
    // No cursor yet: the exclusive upper bound of the window is the starting point, and every row
    // strictly below it is still to come.
    AuditCursor start = AuditCursor.startingAt(AT);

    assertThat(start.at()).isEqualTo(AT);
    assertThat(start.idBoundFor("identity")).isEqualTo(AuditCursor.HIGHEST_ID);
    assertThat(start.idBoundFor("verification")).isEqualTo(AuditCursor.HIGHEST_ID);
  }

  @Test
  void aCursorAlwaysKnowsWhereItIs() {
    assertThatThrownBy(() -> new AuditCursor(null, "identity", ID))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AuditCursor(AT, "  ", ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theSentinelsBoundEveryRealIdTheWayPostgresComparesThem() {
    // HIGHEST_ID and LOWEST_ID are only bounds under the database's unsigned ordering. Under
    // UUID.compareTo they are not: it reads the halves as signed longs, which makes ffff… the
    // smallest id there is and turns "everything at this instant" into "nothing".
    UUID highBitSet = UUID.fromString("9c2f1a5e-0000-4000-8000-000000000001");
    UUID highBitClear = UUID.fromString("2c2f1a5e-0000-4000-8000-000000000001");

    assertThat(AuditCursor.ID_ORDER.compare(AuditCursor.HIGHEST_ID, highBitSet)).isPositive();
    assertThat(AuditCursor.ID_ORDER.compare(AuditCursor.HIGHEST_ID, highBitClear)).isPositive();
    assertThat(AuditCursor.ID_ORDER.compare(AuditCursor.LOWEST_ID, highBitClear)).isNegative();
    assertThat(AuditCursor.HIGHEST_ID.compareTo(highBitClear))
        .describedAs("the signed comparison this exists to avoid, which puts ffff… below 2c2f…")
        .isNegative();
  }

  @Test
  void idsThatShareTheirFirstHalfAreSeparatedByTheirSecond() {
    // Both halves matter: a comparison that stopped at the most significant bits would call these
    // two ids equal, and two entries that compare equal is exactly what a cursor cannot resume
    // from.
    UUID lower = UUID.fromString("3a3a3a3a-3a3a-4a3a-8a3a-000000000001");
    UUID higher = UUID.fromString("3a3a3a3a-3a3a-4a3a-8a3a-000000000002");

    assertThat(AuditCursor.ID_ORDER.compare(lower, higher)).isNegative();
    assertThat(AuditCursor.ID_ORDER.compare(higher, lower)).isPositive();
    assertThat(AuditCursor.ID_ORDER.compare(lower, lower)).isZero();
  }
}

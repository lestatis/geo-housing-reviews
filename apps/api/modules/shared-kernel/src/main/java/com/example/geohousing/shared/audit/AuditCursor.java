package com.example.geohousing.shared.audit;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Where a page of the timeline left off.
 *
 * <p>The timeline merges five independently ordered sources, so a position in it needs more than a
 * timestamp: two modules can record something in the same millisecond. The total order is
 * <strong>at descending, then module ascending, then id descending</strong>, and this carries all
 * three. {@code API_GUIDELINES.md} requires cursor pagination and stable sort semantics for audit
 * events precisely so a reader can walk a long window without gaps or repeats.
 *
 * <p>{@link #idBoundFor} is what lets each source turn that global order into a predicate it can
 * push into SQL, knowing only its own module name.
 *
 * <p>A position and nothing more. How a page is handed back to a caller — and which query it is a
 * position <em>in</em> — belongs to whoever serves the timeline, not to the ordering.
 */
public record AuditCursor(Instant at, String module, UUID id) {

  /**
   * PostgreSQL's ordering of {@code uuid}, which compares the sixteen bytes unsigned.
   *
   * <p>Not {@link UUID#compareTo}, which compares the two halves as <em>signed</em> longs and so
   * puts every UUID with the high bit set — about half of all random ones — below every UUID
   * without it. The keyset predicate is evaluated by Postgres and the merge is evaluated here, so
   * the two orders being the same order is what makes paging correct rather than approximately
   * correct: disagree, and a page boundary drops rows that neither side thinks it skipped.
   */
  public static final Comparator<UUID> ID_ORDER =
      (left, right) -> {
        int high =
            Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0
            ? high
            : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
      };

  /** Sorts above every real UUID, so "everything at this instant" is expressible as a bound. */
  public static final UUID HIGHEST_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

  /** Sorts below every real UUID, so "nothing at this instant" is expressible as the same bound. */
  public static final UUID LOWEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  /**
   * Sorts below every real module name, so a first page excludes nothing. Module names are
   * lowercase letters and {@code !} is below all of them, which is what keeps this from ever
   * colliding with one. Not whitespace, because a blank module is refused.
   */
  private static final String BEFORE_EVERY_MODULE = "!";

  public AuditCursor {
    Objects.requireNonNull(at, "at");
    Objects.requireNonNull(id, "id");
    if (module == null || module.isBlank()) {
      throw new IllegalArgumentException("module must not be blank");
    }
    module = module.trim();
  }

  /**
   * The start of the first page: the window's exclusive upper bound, with nothing at that instant
   * yet excluded.
   */
  public static AuditCursor startingAt(Instant windowEnd) {
    return new AuditCursor(windowEnd, BEFORE_EVERY_MODULE, HIGHEST_ID);
  }

  /**
   * The id every row of {@code sourceModule} must sort below, at exactly this cursor's instant.
   *
   * <p>Three cases, from the total order. The cursor's own module resumes at its own row. A module
   * sorting after it has not been read at this instant at all, so nothing there may be skipped. A
   * module sorting before it is finished with this instant, and only strictly older rows remain.
   */
  public UUID idBoundFor(String sourceModule) {
    int order = sourceModule.compareTo(module);
    if (order > 0) {
      return HIGHEST_ID;
    }
    if (order < 0) {
      return LOWEST_ID;
    }
    return id;
  }
}

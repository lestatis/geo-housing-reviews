package com.example.geohousing.moderation.api;

import java.time.Instant;
import java.util.Optional;

/**
 * What moderation can say about itself without naming anybody.
 *
 * <p>Counts only. Who decided what is the audit timeline's question, and it is answered there with
 * a stated purpose — access review — and a stated risk. A metrics screen that broke these numbers
 * down per moderator would turn the same data into a leaderboard, and a queue served by whoever
 * decides fastest is not a queue served well.
 */
public interface ModerationMetrics {

  /** Cases still needing attention, which is the queue an administrator is asking about. */
  long openCases();

  /**
   * When the oldest case still open was opened, or empty if none is.
   *
   * <p>The number that says whether the queue is being served rather than merely worked: a backlog
   * of ten is fine if none of them is a fortnight old.
   */
  Optional<Instant> oldestOpenCaseAt();

  /**
   * Decisions and appeals in a window, {@code from} inclusive and {@code until} exclusive.
   *
   * <p>From the append-only record rather than from current state. A decision later superseded
   * still happened, and a count that quietly dropped it would describe a history nobody lived.
   */
  ModerationThroughput between(Instant from, Instant until);
}

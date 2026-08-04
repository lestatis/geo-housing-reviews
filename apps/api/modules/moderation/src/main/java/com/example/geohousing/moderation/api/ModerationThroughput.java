package com.example.geohousing.moderation.api;

/**
 * What moderation did in a window.
 *
 * <p>{@code appealsOverturned} is the number {@code docs/MODERATION.md} asks for by name — the only
 * measurement the documents specify, and the one that says whether decisions are being made well
 * rather than merely quickly.
 *
 * @param decisions decisions recorded
 * @param appealsHeard appeals that reached an outcome; a pending appeal has not been heard
 * @param appealsOverturned of those, how many reversed the original decision
 */
public record ModerationThroughput(long decisions, long appealsHeard, long appealsOverturned) {

  public ModerationThroughput {
    if (decisions < 0 || appealsHeard < 0 || appealsOverturned < 0) {
      throw new IllegalArgumentException("a count cannot be negative");
    }
    if (appealsOverturned > appealsHeard) {
      // Not defensive noise: these are two separate queries, and a bad predicate in either would
      // otherwise surface as an overturn rate above 100% on a screen rather than as a failure.
      throw new IllegalArgumentException("more appeals were overturned than were heard");
    }
  }
}
